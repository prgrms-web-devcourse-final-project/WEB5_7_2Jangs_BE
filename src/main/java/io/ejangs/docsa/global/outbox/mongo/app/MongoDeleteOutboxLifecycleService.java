package io.ejangs.docsa.global.outbox.mongo.app;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MongoDeleteOutboxLifecycleService {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;
    private final MongoCreateOperationRepository mongoCreateOperationRepository;

    public MongoIdsDto claimOpen(Long outboxId) {
        int claimed = mongoDeleteOutboxRepository.claimOpenById(outboxId);
        if (claimed == 0) {
            return null;
        }

        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return null;
        }

        return new MongoIdsDto(
                targetOutbox.getSaveContentIds(),
                targetOutbox.getCommitBlockSequenceIds(),
                targetOutbox.getBlockIds()
        );
    }

    public void done(Long outboxId) {
        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markDone();
        markCreateOperationCompensated(targetOutbox);
        mongoDeleteOutboxRepository.save(targetOutbox);
    }

    public void retry(Long outboxId, String errorMessage) {
        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markRetry(errorMessage);
        if (targetOutbox.getStatus() == OutboxStatus.FAILED) {
            markCreateOperationFailed(targetOutbox, errorMessage);
        }
        mongoDeleteOutboxRepository.save(targetOutbox);
    }

    public int recoverTimedOutProcessing(LocalDateTime threshold) {
        List<MongoDeleteOutbox> stuckOutboxes = mongoDeleteOutboxRepository
                .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        OutboxStatus.PROCESSING,
                        threshold
                );
        if (stuckOutboxes.isEmpty()) {
            return 0;
        }

        stuckOutboxes.forEach(outbox -> outbox.recoverProcessingTimeout("PROCESSING timeout recovered"));
        mongoDeleteOutboxRepository.saveAll(stuckOutboxes);
        return stuckOutboxes.size();
    }

    private void markCreateOperationCompensated(MongoDeleteOutbox outbox) {
        if (outbox.getTriggerType() != MongoDeleteOutbox.TriggerType.COMPENSATE) {
            return;
        }
        mongoCreateOperationRepository.findWithLockByOperationId(outbox.getOriginId())
                .filter(operation -> operation.getStatus() == MongoCreateOperationStatus.COMPENSATING)
                .ifPresent(MongoCreateOperation::markCompensated);
    }

    private void markCreateOperationFailed(MongoDeleteOutbox outbox, String errorMessage) {
        if (outbox.getTriggerType() != MongoDeleteOutbox.TriggerType.COMPENSATE) {
            return;
        }
        mongoCreateOperationRepository.findWithLockByOperationId(outbox.getOriginId())
                .filter(operation -> operation.getStatus() == MongoCreateOperationStatus.COMPENSATING)
                .ifPresent(operation -> operation.markCompensationFailed(errorMessage));
    }
}
