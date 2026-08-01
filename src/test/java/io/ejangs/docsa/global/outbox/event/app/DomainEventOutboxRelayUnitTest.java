package io.ejangs.docsa.global.outbox.event.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.app.dispatcher.DomainEventDispatcher;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxRelayUnitTest {

    @Mock
    private DomainEventOutboxRepository repository;

    @Mock
    private DomainEventOutboxLifecycleService lifecycleService;

    @Mock
    private DomainEventDispatcher dispatcher;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("run 한 번은 조회된 100건만 처리하고 다음 batch를 이어서 조회하지 않는다")
    void run_processesOnlyFetchedBatchOnce() {
        DomainEventOutboxRelay relay = relay();
        List<DomainEventOutbox> firstBatch = outboxRows(100);
        when(repository.findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.OPEN))
                .thenReturn(firstBatch);
        when(lifecycleService.claimOpen(anyLong()))
                .thenAnswer(invocation -> processingOutbox(invocation.getArgument(0)));

        relay.run();

        verify(repository).findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.OPEN);
        verify(lifecycleService, times(100)).claimOpen(anyLong());
        verify(dispatcher, times(100)).dispatch(any(DomainEventMessage.class));
        verify(lifecycleService, times(100)).done(anyLong());
    }

    @Test
    @DisplayName("relay 실행 중 들어온 wakeup은 추가 batch 처리로 이어지지 않는다")
    void run_ignoresWakeUpWhileRelayIsRunning() throws Exception {
        DomainEventOutboxRelay relay = relay();
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        List<DomainEventOutbox> firstBatch = outboxRows(1);
        DomainEventOutbox processingOutbox = processingOutbox(1L);
        when(repository.findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.OPEN))
                .thenReturn(firstBatch);
        when(lifecycleService.claimOpen(1L)).thenReturn(processingOutbox);
        org.mockito.Mockito.doAnswer(invocation -> {
            dispatchStarted.countDown();
            assertThat(releaseDispatch.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(dispatcher).dispatch(any(DomainEventMessage.class));

        executor = Executors.newSingleThreadExecutor();
        Future<?> firstRun = executor.submit((Runnable) relay::run);
        assertThat(dispatchStarted.await(5, TimeUnit.SECONDS)).isTrue();

        relay.run();

        releaseDispatch.countDown();
        firstRun.get(5, TimeUnit.SECONDS);
        verify(repository, times(1)).findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.OPEN);
        verify(dispatcher, times(1)).dispatch(any(DomainEventMessage.class));
        verify(lifecycleService, times(1)).done(1L);
    }

    private DomainEventOutboxRelay relay() {
        return new DomainEventOutboxRelay(repository, lifecycleService, dispatcher);
    }

    private List<DomainEventOutbox> outboxRows(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(this::outboxRow)
                .toList();
    }

    private DomainEventOutbox outboxRow(Long id) {
        DomainEventOutbox outbox = mock(DomainEventOutbox.class);
        when(outbox.getId()).thenReturn(id);
        return outbox;
    }

    private DomainEventOutbox processingOutbox(Long id) {
        DomainEventOutbox outbox = mock(DomainEventOutbox.class);
        when(outbox.toMessage()).thenReturn(new DomainEventMessage(
                id,
                DomainEventType.DOC_CREATED,
                AggregateType.DOC,
                id.toString(),
                "{}",
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        return outbox;
    }
}
