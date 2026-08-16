package io.ejangs.docsa.global.outbox.event.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.app.dispatcher.DomainEventDispatcher;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DomainEventOutboxRelayIntegrationTest {

    @Autowired
    private DomainEventOutboxRelay domainEventOutboxRelay;

    @Autowired
    private DomainEventOutboxRepository domainEventOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DomainEventDispatcher domainEventDispatcher;

    @BeforeEach
    void cleanOutbox() {
        domainEventOutboxRepository.deleteAllInBatch();
        domainEventOutboxRepository.flush();
    }

    @Test
    @DisplayName("Domain event relay가 OPEN 건을 처리하면 DONE으로 완료된다")
    void relayDone() {
        DomainEventOutbox outbox = createOpenOutbox();

        domainEventOutboxRelay.run();

        DomainEventOutbox done = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(0);
        assertThat(done.getDoneAt()).isNotNull();
        verify(domainEventDispatcher).dispatch(any(DomainEventMessage.class));
    }

    @Test
    @DisplayName("Domain event relay는 createdAt이 같으면 id 오름차순으로 처리한다")
    void relayDispatchesByCreatedAtAndId() {
        DomainEventOutbox firstOutbox = createOpenOutbox();
        DomainEventOutbox secondOutbox = createOpenOutbox();
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        jdbcTemplate.update(
                "update domain_event_outbox set created_at = ? where id in (?, ?)",
                Timestamp.valueOf(sameCreatedAt),
                firstOutbox.getId(),
                secondOutbox.getId()
        );

        domainEventOutboxRelay.run();

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(domainEventDispatcher, times(2)).dispatch(captor.capture());

        List<Long> dispatchedEventIds = captor.getAllValues().stream()
                .map(DomainEventMessage::eventId)
                .toList();
        assertThat(dispatchedEventIds).containsExactly(firstOutbox.getId(), secondOutbox.getId());
    }

    @Test
    @DisplayName("Domain event relay 처리 중 예외가 발생하면 retryCount 증가 후 OPEN 상태로 복귀한다")
    void relayRetryToOpen() {
        DomainEventOutbox outbox = createOpenOutbox();
        doThrow(new RuntimeException("dispatch fail"))
                .when(domainEventDispatcher).dispatch(any(DomainEventMessage.class));

        domainEventOutboxRelay.run();

        DomainEventOutbox retried = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.OPEN);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getLastError()).contains("dispatch fail");
    }

    @Test
    @DisplayName("Domain event relay 예외가 maxRetry(10회) 누적되면 FAILED 상태가 된다")
    void relayFailedAfterMaxRetry() {
        DomainEventOutbox outbox = createOpenOutbox();
        doThrow(new RuntimeException("always fail"))
                .when(domainEventDispatcher).dispatch(any(DomainEventMessage.class));

        for (int i = 0; i < 10; i++) {
            domainEventOutboxRelay.run();
        }

        DomainEventOutbox failed = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(10);
        assertThat(failed.getLastError()).contains("always fail");
    }

    private DomainEventOutbox createOpenOutbox() {
        DomainEventOutbox outbox = DomainEventOutbox.open(
                DomainEventType.DOC_CREATED,
                AggregateType.DOC,
                UUID.randomUUID().toString(),
                "{}"
        );
        return domainEventOutboxRepository.saveAndFlush(outbox);
    }
}
