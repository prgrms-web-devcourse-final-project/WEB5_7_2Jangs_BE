package io.ejangs.docsa.global.outbox.event.app;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DomainEventOutboxLifecycleService {

    private final DomainEventOutboxRepository domainEventOutboxRepository;

    @Transactional
    public DomainEventOutbox claimOpen(Long outboxId) {
        int claimed = domainEventOutboxRepository.claimOpenById(outboxId);
        if (claimed == 0) {
            return null;
        }

        return domainEventOutboxRepository.findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
    }

    @Transactional
    public void done(Long outboxId) {
        DomainEventOutbox outbox = domainEventOutboxRepository.findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (outbox == null) {
            return;
        }

        outbox.markDone();
        domainEventOutboxRepository.save(outbox);
    }

    @Transactional
    public void retry(Long outboxId, String errorMessage) {
        DomainEventOutbox outbox = domainEventOutboxRepository.findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (outbox == null) {
            return;
        }

        outbox.markRetry(errorMessage);
        domainEventOutboxRepository.save(outbox);
    }

    @Transactional
    public int recoverTimedOutProcessing(LocalDateTime timeoutThreshold) {
        List<DomainEventOutbox> stuckOutboxes =
                domainEventOutboxRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        OutboxStatus.PROCESSING,
                        timeoutThreshold
                );
        if (stuckOutboxes.isEmpty()) {
            return 0;
        }

        stuckOutboxes.forEach(outbox ->
                outbox.recoverProcessingTimeout("PROCESSING timeout recovered")
        );
        domainEventOutboxRepository.saveAll(stuckOutboxes);
        return stuckOutboxes.size();
    }
}
