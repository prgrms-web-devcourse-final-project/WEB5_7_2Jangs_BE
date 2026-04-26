package io.ejangs.docsa.global.outbox.s3.app;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.s3.dao.S3DeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.s3.dto.S3DeleteTarget;
import io.ejangs.docsa.global.outbox.s3.entity.S3DeleteOutbox;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "s3.delete.outbox.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class S3DeleteOutboxWorker {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final S3DeleteOutboxRepository s3DeleteOutboxRepository;
    private final S3DeleteService s3DeleteService;
    private final S3DeleteOutboxLifecycleService s3DeleteOutboxLifecycleService;

    @Scheduled(
            fixedDelayString = "${s3.delete.outbox.worker.fixed-delay:PT1M}",
            initialDelayString = "${s3.delete.outbox.worker.initial-delay:PT0S}"
    )
    public void run() {
        int recovered = s3DeleteOutboxLifecycleService.recoverTimedOutProcessing(
                LocalDateTime.now().minus(PROCESSING_TIMEOUT)
        );
        if (recovered > 0) {
            log.warn("[S3 Outbox Worker] Recovered timed-out PROCESSING rows: {}", recovered);
        }

        List<S3DeleteOutbox> outboxes = s3DeleteOutboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.OPEN);
        if (outboxes.isEmpty()) {
            return;
        }

        deleteTargets(outboxes);
        log.info("[S3 Outbox Worker] Complete target delete : {}", outboxes.size());
    }

    private void deleteTargets(List<S3DeleteOutbox> outboxes) {
        for (S3DeleteOutbox outbox : outboxes) {
            S3DeleteTarget target = s3DeleteOutboxLifecycleService.claimOpen(outbox.getId());
            if (target == null) {
                continue;
            }

            try {
                s3DeleteService.deleteTarget(target);
                s3DeleteOutboxLifecycleService.done(outbox.getId());
            } catch (Exception e) {
                log.error("[S3 Outbox Worker] Error : {}", e.getMessage(), e);
                s3DeleteOutboxLifecycleService.retry(outbox.getId(), e.getMessage());
            }
        }
    }
}
