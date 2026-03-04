package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
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
    private final MongoDeleteOutboxCompleteService mongoDeleteOutboxCompleteService;

    @Scheduled(fixedDelayString = "PT1M")
    public void run() {
        int recovered = mongoDeleteOutboxCompleteService.recoverTimedOutProcessing(
                LocalDateTime.now().minus(PROCESSING_TIMEOUT)
        );
        if (recovered > 0) {
            log.warn("[Outbox Worker] Recovered timed-out PROCESSING rows: {}", recovered);
        }

        List<MongoDeleteOutbox> outboxes = mongoDeleteOutboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(MongoDeleteOutbox.OutboxStatus.OPEN);
        log.info("[Outbox Worker] Target size : {}", outboxes.size());
        if (outboxes.isEmpty()) {
            return;
        }
        deleteTarget(outboxes);
        log.info("[Outbox Worker] Complete Target delete : {}", outboxes.size());
    }

    private void deleteTarget(List<MongoDeleteOutbox> outboxes) {
        for (MongoDeleteOutbox outbox : outboxes) {
            MongoIdsDto target = mongoDeleteOutboxCompleteService.claimOpen(outbox.getId());
            if (target == null) {
                continue;
            }
            try {
                mongoDeleteService.deleteTarget(target);
                mongoDeleteOutboxCompleteService.done(outbox.getId());
            } catch (Exception e) {
                log.error("[Outbox Worker] Error : {}", e.getMessage(), e);
                mongoDeleteOutboxCompleteService.retry(outbox.getId(), e.getMessage());
            }
        }
    }

}
