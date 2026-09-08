package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MongoCreateCompensationService {

    private final MongoCreateOperationRepository operationRepository;
    private final MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void request(String operationId, String errorMessage) {
        MongoCreateOperation operation = operationRepository
                .findWithLockByOperationId(operationId)
                .orElse(null);
        if (operation == null || operation.getStatus() != MongoCreateOperationStatus.PENDING) {
            return;
        }

        operation.startCompensating(errorMessage);
        mongoDeleteJobEnqueuer.enqueueCreateCompensation(
                operationId,
                DomainType.valueOf(operation.getOperationType().name()),
                operation.mongoIds()
        );
    }
}
