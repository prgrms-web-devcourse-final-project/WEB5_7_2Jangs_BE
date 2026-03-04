package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDeleteOutboxCompleteService {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Transactional
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

    @Transactional
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

    @Transactional
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
}
