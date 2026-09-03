package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "mongo.create.operation.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MongoCreateOperationRecoveryWorker {

    private final MongoCreateOperationRepository operationRepository;
    private final MongoCreateCompensationService compensationService;

    @Value("${mongo.create.operation.worker.pending-timeout:PT5M}")
    private Duration pendingTimeout;

    @Scheduled(
            fixedDelayString = "${mongo.create.operation.worker.fixed-delay:PT1M}",
            initialDelayString = "${mongo.create.operation.worker.initial-delay:PT0S}"
    )
    public void run() {
        List<MongoCreateOperation> staleOperations = operationRepository
                .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        MongoCreateOperationStatus.PENDING,
                        LocalDateTime.now().minus(pendingTimeout)
                );

        for (MongoCreateOperation operation : staleOperations) {
            try {
                compensationService.request(operation.getOperationId(), "PENDING timeout recovered");
            } catch (Exception e) {
                log.error("[Create Saga Worker] 보상 Outbox 등록 실패: operationId={}",
                        operation.getOperationId(), e);
            }
        }
    }
}
