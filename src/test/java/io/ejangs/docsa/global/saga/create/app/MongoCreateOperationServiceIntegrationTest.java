package io.ejangs.docsa.global.saga.create.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.config.JpaConfig;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.dao.MongoCreateOperationRepository;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
        MongoCreateOperationCompletionService.class
})
class MongoCreateOperationServiceIntegrationTest {

    @Autowired
    private MongoCreateOperationService operationService;

    @Autowired
    private MongoCreateOperationRepository operationRepository;

    @Autowired
    private MongoCreateOperationCompletionService completionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PENDING 저장이 커밋된 뒤에만 신규 생성 시작을 허용한다")
    void startsAfterPendingIsPersisted() {
        MongoIdsDto plannedIds = new MongoIdsDto(List.of("save-1"), List.of(), List.of());
        String operationId = UUID.randomUUID().toString();

        MongoCreateOperationStart start = operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-1",
                plannedIds
        );

        MongoCreateOperation saved = operationRepository.findById(operationId).orElseThrow();
        assertThat(start.started()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(MongoCreateOperationStatus.PENDING);
        assertThat(saved.mongoIds()).isEqualTo(plannedIds);
    }

    @Test
    @DisplayName("블록 ID 200개를 생성 작업 원장의 단일 JSON 컬럼에 저장한다")
    void storesPlannedMongoIdsInSingleJsonColumn() {
        List<String> blockIds = IntStream.range(0, 200)
                .mapToObj(index -> "block-" + index)
                .toList();
        MongoIdsDto plannedIds = new MongoIdsDto(
                List.of(),
                List.of("commit-1"),
                blockIds
        );
        String operationId = UUID.randomUUID().toString();

        operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.COMMIT,
                "hash-1",
                plannedIds
        );

        String storedJson = jdbcTemplate.queryForObject(
                "select cast(mongo_ids as varchar) from mongo_create_operation where operation_id = ?",
                String.class,
                operationId
        );
        assertThat(storedJson)
                .contains("commit-1")
                .contains("block-0")
                .contains("block-199");
    }

    @Test
    @DisplayName("같은 operationId의 PENDING 작업은 Mongo 생성을 다시 시작하지 않는다")
    void rejectsDuplicatePendingOperation() {
        MongoIdsDto plannedIds = new MongoIdsDto(List.of("save-1"), List.of(), List.of());
        String operationId = UUID.randomUUID().toString();
        long countBefore = operationRepository.count();
        operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-1",
                plannedIds
        );

        assertThatThrownBy(() -> operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-1",
                plannedIds
        ))
                .isInstanceOf(CustomException.class)
                .hasMessage("같은 생성 요청이 처리 중입니다.");

        assertThat(operationRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    @DisplayName("같은 operationId를 다른 요청에 재사용하면 충돌로 거절한다")
    void rejectsOperationIdReusedForDifferentRequest() {
        String operationId = UUID.randomUUID().toString();
        operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-1",
                new MongoIdsDto(List.of("save-1"), List.of(), List.of())
        );

        assertThatThrownBy(() -> operationService.start(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "hash-2",
                new MongoIdsDto(List.of("save-2"), List.of(), List.of())
        ))
                .isInstanceOf(CustomException.class)
                .hasMessage("Idempotency-Key가 다른 생성 요청에 이미 사용되었습니다.");
    }

    @Test
    @DisplayName("MySQL 생성과 COMPLETED가 커밋된 뒤 같은 operationId는 기존 결과를 반환한다")
    void returnsCompletedResultAfterResponseLoss() {
        String operationId = UUID.randomUUID().toString();
        MongoIdsDto plan = new MongoIdsDto(List.of("save-1"), List.of(), List.of());
        operationService.start(
                operationId, 1L, MongoCreateOperationType.DOC, "hash-1", plan
        );
        requiresNewTransaction().executeWithoutResult(status -> {
            MongoCreateOperation operation = completionService.lockPending(operationId);
            completionService.complete(operation, 10L, 20L);
        });

        MongoCreateOperationStart existing = operationService.findExisting(
                operationId, 1L, MongoCreateOperationType.DOC, "hash-1"
        ).orElseThrow();

        assertThat(existing.started()).isFalse();
        assertThat(existing.resultEntityId()).isEqualTo(10L);
        assertThat(existing.resultSaveId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("최종 MySQL 트랜잭션이 실패하면 COMPLETED 전환도 롤백된다")
    void rollsBackCompletionWithFinalMySqlTransaction() {
        String operationId = UUID.randomUUID().toString();
        MongoIdsDto plan = new MongoIdsDto(List.of("save-1"), List.of(), List.of());
        operationService.start(
                operationId, 1L, MongoCreateOperationType.DOC, "hash-1", plan
        );

        assertThatThrownBy(() -> requiresNewTransaction().executeWithoutResult(status -> {
            MongoCreateOperation operation = completionService.lockPending(operationId);
            completionService.complete(operation, 10L, 20L);
            throw new RuntimeException("final mysql transaction failed");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("final mysql transaction failed");

        assertThat(operationRepository.findById(operationId).orElseThrow().getStatus())
                .isEqualTo(MongoCreateOperationStatus.PENDING);
    }

    @Test
    @DisplayName("같은 operationId가 동시에 들어와도 PENDING 작업은 하나만 생성된다")
    void concurrentStartCreatesSingleOperation() throws Exception {
        String operationId = UUID.randomUUID().toString();
        MongoIdsDto plan = new MongoIdsDto(List.of("save-1"), List.of(), List.of());
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> startAtBarrier(
                    barrier, operationId, plan));
            Future<Object> second = executor.submit(() -> startAtBarrier(
                    barrier, operationId, plan));

            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(results.stream().filter(MongoCreateOperationStart.class::isInstance))
                    .hasSize(1);
            assertThat(results.stream().filter(CustomException.class::isInstance))
                    .hasSize(1);
            assertThat(operationRepository.findById(operationId)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object startAtBarrier(
            CyclicBarrier barrier,
            String operationId,
            MongoIdsDto plan
    ) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return operationService.start(
                    operationId, 1L, MongoCreateOperationType.DOC, "hash-1", plan
            );
        } catch (Throwable e) {
            return e;
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
