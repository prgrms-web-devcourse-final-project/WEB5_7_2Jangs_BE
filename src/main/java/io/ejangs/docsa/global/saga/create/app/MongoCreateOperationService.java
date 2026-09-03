package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CreateOperationErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MongoCreateOperationService {

    private final MongoCreateOperationRepository operationRepository;
    private final MongoCreateOperationCreator operationCreator;

    public Optional<MongoCreateOperationStart> findExisting(
            String operationId,
            Long userId,
            MongoCreateOperationType operationType,
            String requestHash
    ) {
        return operationRepository.findById(operationId)
                .map(operation -> resolveExisting(operation, userId, operationType, requestHash));
    }

    public MongoCreateOperationStart start(
            String operationId,
            Long userId,
            MongoCreateOperationType operationType,
            String requestHash,
            MongoIdsDto mongoIds
    ) {
        try {
            operationCreator.create(operationId, userId, operationType, requestHash, mongoIds);
            return MongoCreateOperationStart.newOperation();
        } catch (DataIntegrityViolationException e) {
            MongoCreateOperation raced = operationRepository.findById(operationId).orElseThrow(() -> e);
            return resolveExisting(raced, userId, operationType, requestHash);
        }
    }

    private MongoCreateOperationStart resolveExisting(
            MongoCreateOperation operation,
            Long userId,
            MongoCreateOperationType operationType,
            String requestHash
    ) {
        if (!operation.getUserId().equals(userId)
                || operation.getOperationType() != operationType
                || !operation.getRequestHash().equals(requestHash)) {
            throw new CustomException(CreateOperationErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        if (operation.getStatus() == MongoCreateOperationStatus.COMPLETED) {
            return MongoCreateOperationStart.completed(
                    operation.getResultEntityId(),
                    operation.getResultSaveId()
            );
        }
        if (operation.getStatus() == MongoCreateOperationStatus.PENDING) {
            throw new CustomException(CreateOperationErrorCode.IN_PROGRESS);
        }
        throw new CustomException(CreateOperationErrorCode.CANCELLED);
    }
}
