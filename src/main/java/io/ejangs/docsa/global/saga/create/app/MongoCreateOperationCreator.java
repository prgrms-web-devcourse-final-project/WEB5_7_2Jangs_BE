package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MongoCreateOperationCreator {

    private final MongoCreateOperationRepository operationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void create(
            String operationId,
            Long userId,
            MongoCreateOperationType operationType,
            String requestHash,
            MongoIdsDto mongoIds
    ) {
        operationRepository.saveAndFlush(MongoCreateOperation.pending(
                operationId,
                userId,
                operationType,
                requestHash,
                mongoIds
        ));
    }
}
