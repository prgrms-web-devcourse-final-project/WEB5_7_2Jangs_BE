package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
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
public class MongoDeleteOutboxCompleteService {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    public MongoIdsDto claimOpen(Long outboxId) {
        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, MongoDeleteOutbox.OutboxStatus.OPEN)
                .orElse(null);
        if (targetOutbox == null) {
            return null;
        }

        targetOutbox.markProcessing();
        mongoDeleteOutboxRepository.save(targetOutbox);
        return new MongoIdsDto(
                targetOutbox.getSaveContentIds(),
                targetOutbox.getCommitBlockSequenceIds(),
                targetOutbox.getBlockIds()
        );
    }

    public void done(Long outboxId) {
        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, MongoDeleteOutbox.OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markDone();
        mongoDeleteOutboxRepository.save(targetOutbox);
    }

    public void retry(Long outboxId, String errorMessage) {
        MongoDeleteOutbox targetOutbox = mongoDeleteOutboxRepository
                .findByIdAndStatus(outboxId, MongoDeleteOutbox.OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markRetry(errorMessage);
        mongoDeleteOutboxRepository.save(targetOutbox);
    }

    public int recoverTimedOutProcessing(LocalDateTime threshold) {
        List<MongoDeleteOutbox> stuckOutboxes = mongoDeleteOutboxRepository
                .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        MongoDeleteOutbox.OutboxStatus.PROCESSING,
                        threshold
                );
        if (stuckOutboxes.isEmpty()) {
            return 0;
        }

        stuckOutboxes.forEach(outbox -> outbox.markRetry(outbox.getLastError() + " (recovered)"));
        mongoDeleteOutboxRepository.saveAll(stuckOutboxes);
        return stuckOutboxes.size();
    }
}
