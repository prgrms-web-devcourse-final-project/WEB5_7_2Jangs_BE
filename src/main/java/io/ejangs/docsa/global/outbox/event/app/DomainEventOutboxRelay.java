package io.ejangs.docsa.global.outbox.event.app;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.app.dispatcher.DomainEventDispatcher;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "domain.event.outbox.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DomainEventOutboxRelay {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final DomainEventOutboxRepository repository;
    private final DomainEventOutboxLifecycleService lifecycleService;
    private final DomainEventDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString = "${domain.event.outbox.worker.fixed-delay:PT10S}",
            initialDelayString = "${domain.event.outbox.worker.initial-delay:PT5S}"
    )
    public void runScheduled() {
        run();
    }

    public void run() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            doRun();
        } finally {
            running.set(false);
        }
    }

    @Deprecated
    public void run(Long outboxId) {
        run();
    }

    private void doRun() {
        recoverTimedOutProcessing();

        List<DomainEventOutbox> events =
                repository.findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.OPEN);

        for (DomainEventOutbox event : events) {
            processOne(event.getId());
        }
    }

    private void processOne(Long outboxId) {
        DomainEventOutbox event = lifecycleService.claimOpen(outboxId);
        if (event == null) {
            return;
        }

        try {
            dispatcher.dispatch(event.toMessage());
            lifecycleService.done(outboxId);
        } catch (Exception e) {
            log.error("[DomainEventOutboxRelay] Dispatch failed. outboxId={}, message={}",
                    outboxId, e.getMessage(), e);
            lifecycleService.retry(outboxId, e.getMessage());
        }
    }

    private void recoverTimedOutProcessing() {
        int recovered = lifecycleService.recoverTimedOutProcessing(
                LocalDateTime.now().minus(PROCESSING_TIMEOUT)
        );

        if (recovered > 0) {
            log.warn("[DomainEventOutboxRelay] Recovered timed-out PROCESSING rows: {}", recovered);
        }
    }
}
