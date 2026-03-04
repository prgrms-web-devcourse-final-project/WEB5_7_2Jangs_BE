package io.ejangs.docsa.global.mongo.deletion.entity;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OperationSource;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OperationType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MongoDeleteOutboxFactory {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Transactional
    public MongoDeleteOutbox create(
            OperationType operationType,
            OperationSource operationSource,
            Long targetId,
            String targetMongoId,
            MongoIdsDto ids
    ) {

        Objects.requireNonNull(operationType, "operationType is required");
        Objects.requireNonNull(operationSource, "operationSource is required");
        Objects.requireNonNull(ids, "mongo ids is required");

        if (ids.saveContentsIds().isEmpty()
                && ids.commitBlockSequenceIds().isEmpty()
                && ids.blockIds().isEmpty()) {
            return null;
        }

        String operationKey;
        if (operationSource == OperationSource.COMPENSATION) {
            if (targetMongoId == null) {
                throw new IllegalArgumentException("targetMongoId is required");
            }
            operationKey = MongoDeleteOutbox.buildOperationKey(
                    operationSource, operationType, "MONGO_ID", targetMongoId
            );
        } else {
            if (targetId == null || targetId <= 0) {
                throw new IllegalArgumentException("targetId is required");
            }
            operationKey = MongoDeleteOutbox.buildOperationKey(
                    operationSource, operationType, "DOMAIN_ID", String.valueOf(targetId)
            );
        }

        MongoDeleteOutbox existing = mongoDeleteOutboxRepository.findByOperationKey(operationKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        MongoDeleteOutbox newOutbox = MongoDeleteOutbox.open(
                operationType,
                operationSource,
                operationKey,
                targetId,
                targetMongoId,
                ids.saveContentsIds(),
                ids.commitBlockSequenceIds(),
                ids.blockIds()
        );

        try {
            return mongoDeleteOutboxRepository.save(newOutbox);
        } catch (DataIntegrityViolationException e) {
            return mongoDeleteOutboxRepository.findByOperationKey(operationKey)
                    .orElseThrow(() -> e);
        }
    }
}
