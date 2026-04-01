package io.ejangs.docsa.global.mongo.outbox.app;

import io.ejangs.docsa.global.mongo.outbox.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDeleteOutboxWorker {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;
    private final MongoDeleteService mongoDeleteService;
    private final MongoDeleteOutboxLifecycleService mongoDeleteOutboxLifecycleService;

    @Scheduled(
            fixedDelayString = "${mongo.delete.outbox.worker.fixed-delay:PT1M}",
            initialDelayString = "${mongo.delete.outbox.worker.initial-delay:PT0S}"
    )
    public void run() {
        int recovered = mongoDeleteOutboxLifecycleService.recoverTimedOutProcessing(
                LocalDateTime.now().minus(PROCESSING_TIMEOUT)
        );
        if (recovered > 0) {
            log.warn("[Outbox Worker] Recovered timed-out PROCESSING rows: {}", recovered);
        }

        List<MongoDeleteOutbox> outboxes = mongoDeleteOutboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(MongoDeleteOutbox.OutboxStatus.OPEN);
        if (outboxes.isEmpty()) {
            return;
        }
        deleteTarget(outboxes);
        log.info("[Outbox Worker] Complete Target delete : {}", outboxes.size());
    }

    private void deleteTarget(List<MongoDeleteOutbox> outboxes) {
        for (MongoDeleteOutbox outbox : outboxes) {
            MongoIdsDto target = mongoDeleteOutboxLifecycleService.claimOpen(outbox.getId());
            if (target == null) {
                continue;
            }
            try {
                mongoDeleteService.deleteTarget(target);
                mongoDeleteOutboxLifecycleService.done(outbox.getId());
            } catch (Exception e) {
                log.error("[Outbox Worker] Error : {}", e.getMessage(), e);
                mongoDeleteOutboxLifecycleService.retry(outbox.getId(), e.getMessage());
            }
        }
    }

}
