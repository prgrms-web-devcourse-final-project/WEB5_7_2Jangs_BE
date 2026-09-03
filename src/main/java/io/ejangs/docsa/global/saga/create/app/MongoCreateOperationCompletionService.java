package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CreateOperationErrorCode;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoCreateOperationCompletionService {

    private final MongoCreateOperationRepository operationRepository;

    public MongoCreateOperation lockPending(String operationId) {
        MongoCreateOperation operation = operationRepository
                .findWithLockByOperationId(operationId)
                .orElseThrow(() -> new CustomException(CreateOperationErrorCode.INVALID_STATE));
        if (operation.getStatus() != MongoCreateOperationStatus.PENDING) {
            throw new CustomException(CreateOperationErrorCode.INVALID_STATE);
        }
        return operation;
    }

    public void complete(
            MongoCreateOperation operation,
            Long resultEntityId,
            Long resultSaveId
    ) {
        operation.complete(resultEntityId, resultSaveId);
    }
}
