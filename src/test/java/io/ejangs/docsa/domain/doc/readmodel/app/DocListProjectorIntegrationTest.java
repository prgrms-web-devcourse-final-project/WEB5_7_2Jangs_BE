package io.ejangs.docsa.domain.doc.readmodel.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelay;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DocListProjectorIntegrationTest {

    @Autowired
    private DomainEventOutboxRelay domainEventOutboxRelay;

    @Autowired
    private DomainEventOutboxRepository domainEventOutboxRepository;

    @Autowired
    private DocListReadModelRepository docListReadModelRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long docId;

    @BeforeEach
    void setUp() {
        docId = System.currentTimeMillis();
        domainEventOutboxRepository.deleteAllInBatch();
        domainEventOutboxRepository.flush();
        docListReadModelRepository.deleteById(docId);
    }

    @AfterEach
    void cleanUp() {
        docListReadModelRepository.deleteById(docId);
    }

    @Test
    @DisplayName("Domain event relay가 DOC_CREATED를 dispatch하면 문서 목록 read model이 생성된다")
    void relayProjector_success_docCreated() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        DocCreatedPayload payload = new DocCreatedPayload(
                docId,
                2L,
                "테스트 문서",
                createdAt,
                updatedAt,
                10L,
                "thumbnail-1",
                ThumbnailStatus.READY
        );
        DomainEventOutbox outbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_CREATED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(payload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(payload),
                outbox.getId()
        );

        domainEventOutboxRelay.run();

        DomainEventOutbox done = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
        Optional<DocListReadModel> readModel = docListReadModelRepository.findById(docId);

        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(readModel).isPresent();
        DocListReadModel model = readModel.get();
        assertThat(model.getId()).isEqualTo(docId);
        assertThat(model.getUserId()).isEqualTo(2L);
        assertThat(model.getTitle()).isEqualTo("테스트 문서");
        assertThat(model.getCreatedAt()).isEqualTo(createdAt);
        assertThat(model.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(model.getRecentSaveId()).isEqualTo(10L);
        assertThat(model.getThumbnailObjectKey()).isEqualTo("thumbnail-1");
        assertThat(model.getThumbnailStatus()).isEqualTo(ThumbnailStatus.READY);
        assertThat(model.getLastProjectedEventId()).isEqualTo(outbox.getId());
    }

    @Test
    @DisplayName("변경 이벤트가 read model보다 먼저 처리되면 outbox를 DONE 처리하지 않고 재시도 대상으로 남긴다")
    void relayProjector_retry_whenReadModelMissingForUpdateEvent() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);
        DocTitleChangedPayload payload = new DocTitleChangedPayload(docId, "변경 제목", updatedAt);
        DomainEventOutbox outbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_TITLE_CHANGED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(payload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(payload),
                outbox.getId()
        );

        domainEventOutboxRelay.run();

        DomainEventOutbox retried = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
        Optional<DocListReadModel> readModel = docListReadModelRepository.findById(docId);

        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.OPEN);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getDoneAt()).isNull();
        assertThat(retried.getLastError()).contains("Doc list read model is missing");
        assertThat(readModel).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("변경 이벤트 id로 relay를 깨워도 생성 이벤트부터 순서대로 처리된다")
    void relayProjector_success_processCreatedEventBeforeChangeEventWhenWakeUpWithChangeEventId() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime initialUpdatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        LocalDateTime titleUpdatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);
        DocCreatedPayload createdPayload = new DocCreatedPayload(
                docId,
                2L,
                "초기 제목",
                createdAt,
                initialUpdatedAt,
                10L,
                "thumbnail-1",
                ThumbnailStatus.READY
        );
        DomainEventOutbox createdOutbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_CREATED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(createdPayload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(createdPayload),
                createdOutbox.getId()
        );

        DocTitleChangedPayload titlePayload = new DocTitleChangedPayload(docId, "변경 제목", titleUpdatedAt);
        DomainEventOutbox titleOutbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_TITLE_CHANGED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(titlePayload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(titlePayload),
                titleOutbox.getId()
        );

        domainEventOutboxRelay.run(titleOutbox.getId());

        DomainEventOutbox doneCreated = domainEventOutboxRepository.findById(createdOutbox.getId()).orElseThrow();
        DomainEventOutbox doneTitle = domainEventOutboxRepository.findById(titleOutbox.getId()).orElseThrow();
        DocListReadModel readModel = docListReadModelRepository.findById(docId).orElseThrow();

        assertThat(doneCreated.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(doneTitle.getStatus())
                .as("retryCount=%s, lastError=%s", doneTitle.getRetryCount(), doneTitle.getLastError())
                .isEqualTo(OutboxStatus.DONE);
        assertThat(doneTitle.getRetryCount()).isEqualTo(0);
        assertThat(doneTitle.getLastError()).isNull();
        assertThat(readModel.getTitle()).isEqualTo("변경 제목");
        assertThat(readModel.getUpdatedAt()).isEqualTo(titleUpdatedAt);
        assertThat(readModel.getLastProjectedEventId()).isEqualTo(titleOutbox.getId());
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("run(outboxId)는 특정 row만 처리하지 않고 OPEN 이벤트를 생성 순서대로 처리한다")
    void relayProjector_processOpenEventsInCreatedOrderWhenWakeUpWithSpecificId() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime initialUpdatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        LocalDateTime titleUpdatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);

        DocCreatedPayload createdPayload = new DocCreatedPayload(
                docId,
                2L,
                "초기 제목",
                createdAt,
                initialUpdatedAt,
                10L,
                "thumbnail-1",
                ThumbnailStatus.READY
        );
        DomainEventOutbox createdOutbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_CREATED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(createdPayload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(createdPayload),
                createdOutbox.getId()
        );

        DocTitleChangedPayload titlePayload = new DocTitleChangedPayload(docId, "변경 제목", titleUpdatedAt);
        DomainEventOutbox titleOutbox = domainEventOutboxRepository.saveAndFlush(
                DomainEventOutbox.open(
                        DomainEventType.DOC_TITLE_CHANGED,
                        AggregateType.DOC,
                        docId.toString(),
                        objectMapper.writeValueAsString(titlePayload)
                )
        );
        jdbcTemplate.update(
                "update domain_event_outbox set payload = ? format json where id = ?",
                objectMapper.writeValueAsString(titlePayload),
                titleOutbox.getId()
        );

        domainEventOutboxRelay.run(titleOutbox.getId());

        DomainEventOutbox doneCreated = domainEventOutboxRepository.findById(createdOutbox.getId()).orElseThrow();
        DomainEventOutbox doneTitle = domainEventOutboxRepository.findById(titleOutbox.getId()).orElseThrow();
        DocListReadModel readModel = docListReadModelRepository.findById(docId).orElseThrow();

        assertThat(doneCreated.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(doneTitle.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(doneTitle.getRetryCount()).isEqualTo(0);
        assertThat(readModel.getTitle()).isEqualTo("변경 제목");
        assertThat(readModel.getUpdatedAt()).isEqualTo(titleUpdatedAt);
    }
}
