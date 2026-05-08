package io.ejangs.docsa.domain.doc.readmodel.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
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
}
