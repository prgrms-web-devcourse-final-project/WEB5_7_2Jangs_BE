package io.ejangs.docsa.global.saga.create.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ejangs.docsa.global.config.JpaConfig;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteOutboxLifecycleService;
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaConfig.class,
        MongoCreateOperationService.class,
        MongoCreateOperationCreator.class,
        MongoCreateCompensationService.class,
        MongoCreateOperationRecoveryWorker.class,
        MongoDeleteJobEnqueuer.class,
        MongoDeleteOutboxLifecycleService.class
})
class MongoCreateCompensationIntegrationTest {

    @Autowired
    private MongoCreateOperationService operationService;

    @Autowired
    private MongoCreateCompensationService compensationService;

    @Autowired
    private MongoCreateOperationRepository operationRepository;

    @Autowired
    private MongoDeleteOutboxRepository outboxRepository;

    @Autowired
    private MongoDeleteOutboxLifecycleService outboxLifecycleService;

    @Autowired
    private MongoCreateOperationRecoveryWorker recoveryWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("PENDING 작업을 COMPENSATING으로 바꾸면서 삭제 Outbox를 같은 트랜잭션에 등록한다")
    void startsCompensationWithDeleteOutbox() {
        String operationId = startPendingOperation();

        compensationService.request(operationId, "mysql create failed");

        MongoCreateOperation operation = operationRepository.findById(operationId).orElseThrow();
        MongoDeleteOutbox outbox = findCompensationOutbox(operationId);
        assertThat(operation.getStatus()).isEqualTo(MongoCreateOperationStatus.COMPENSATING);
        assertThat(operation.getLastError()).isEqualTo("mysql create failed");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.OPEN);
        assertThat(outbox.getSaveContentIds()).containsExactly("save-1");
    }

    @Test
    @DisplayName("보상 요청을 재시도해도 생성 작업과 Outbox는 중복되지 않는다")
    void compensationRequestIsIdempotent() {
        String operationId = startPendingOperation();

        compensationService.request(operationId, "first failure");
        compensationService.request(operationId, "second failure");

        assertThat(outboxRepository.findAll().stream()
                .filter(outbox -> operationId.equals(outbox.getOriginId())))
                .hasSize(1);
        assertThat(operationRepository.findById(operationId).orElseThrow().getStatus())
                .isEqualTo(MongoCreateOperationStatus.COMPENSATING);
    }

    @Test
    @DisplayName("보상 중인 operationId를 재요청해도 생성을 다시 시작하지 않는다")
    void compensatedRequestCannotRestartCreation() {
        String operationId = startPendingOperation();
        compensationService.request(operationId, "mysql create failed");

        assertThatThrownBy(() -> operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-" + operationId,
                new MongoIdsDto(List.of("another-save"), List.of(), List.of())
        ))
                .isInstanceOf(CustomException.class)
                .hasMessage("취소된 생성 요청입니다. 새 Idempotency-Key로 다시 요청해주세요.");

        assertThat(outboxRepository.findAll().stream()
                .filter(outbox -> operationId.equals(outbox.getOriginId())))
                .hasSize(1);
    }

    @Test
    @DisplayName("보상 Outbox가 DONE이 되면 생성 작업도 COMPENSATED가 된다")
    void marksOperationCompensatedWithOutboxDone() {
        String operationId = startPendingOperation();
        compensationService.request(operationId, "mysql create failed");
        MongoDeleteOutbox outbox = findCompensationOutbox(operationId);
        outboxLifecycleService.claimOpen(outbox.getId());

        outboxLifecycleService.done(outbox.getId());

        assertThat(operationRepository.findById(operationId).orElseThrow().getStatus())
                .isEqualTo(MongoCreateOperationStatus.COMPENSATED);
    }

    @Test
    @DisplayName("보상 Outbox가 재시도 한도에 도달하면 생성 작업도 FAILED가 된다")
    void marksOperationFailedWithOutboxFailure() {
        String operationId = startPendingOperation();
        compensationService.request(operationId, "mysql create failed");
        MongoDeleteOutbox outbox = findCompensationOutbox(operationId);

        for (int i = 0; i < 10; i++) {
            outboxLifecycleService.claimOpen(outbox.getId());
            outboxLifecycleService.retry(outbox.getId(), "mongo delete failed");
        }

        MongoCreateOperation operation = operationRepository.findById(operationId).orElseThrow();
        assertThat(operation.getStatus()).isEqualTo(MongoCreateOperationStatus.FAILED);
        assertThat(operation.getLastError()).isEqualTo("mongo delete failed");
    }

    @Test
    @DisplayName("일정 시간 이상 PENDING인 작업은 복구 Worker가 보상 Outbox로 전환한다")
    void recoversStalePendingOperation() {
        String operationId = startPendingOperation();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "update mongo_create_operation set updated_at = ? where operation_id = ?",
                    LocalDateTime.now().minusMinutes(10),
                    operationId
            ));

        recoveryWorker.run();
        entityManager.clear();

        assertThat(operationRepository.findById(operationId).orElseThrow().getStatus())
                .isEqualTo(MongoCreateOperationStatus.COMPENSATING);
        assertThat(findCompensationOutbox(operationId).getStatus()).isEqualTo(OutboxStatus.OPEN);
    }

    private String startPendingOperation() {
        String operationId = UUID.randomUUID().toString();
        operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-" + operationId,
                new MongoIdsDto(List.of("save-1"), List.of(), List.of())
        );
        return operationId;
    }

    private MongoDeleteOutbox findCompensationOutbox(String operationId) {
        return outboxRepository.findByTriggerTypeAndDomainTypeAndOriginId(
                MongoDeleteOutbox.TriggerType.COMPENSATE,
                MongoDeleteOutbox.DomainType.DOC,
                operationId
        ).orElseThrow();
    }
}
