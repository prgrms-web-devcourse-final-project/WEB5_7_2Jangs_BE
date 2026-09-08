package io.ejangs.docsa.global.saga.create.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MongoCreateOperationTest {

    @Test
    @DisplayName("생성 작업은 PENDING으로 시작하고 사전 확정한 Mongo ID를 보관한다")
    void startsPendingWithPlannedMongoIds() {
        MongoIdsDto plannedIds = new MongoIdsDto(
                List.of("save-1"),
                List.of("commit-1"),
                List.of("block-1", "block-2")
        );

        MongoCreateOperation operation = MongoCreateOperation.pending(
                "550e8400-e29b-41d4-a716-446655440000",
                1L,
                MongoCreateOperationType.COMMIT,
                "request-hash",
                plannedIds
        );

        assertThat(operation.getStatus()).isEqualTo(MongoCreateOperationStatus.PENDING);
        assertThat(operation.mongoIds()).isEqualTo(plannedIds);
    }

    @Test
    @DisplayName("PENDING 작업은 생성 결과 ID를 기록하면서 COMPLETED로 전환한다")
    void completesPendingOperation() {
        MongoCreateOperation operation = pendingOperation();

        operation.complete(10L, 20L);

        assertThat(operation.getStatus()).isEqualTo(MongoCreateOperationStatus.COMPLETED);
        assertThat(operation.getResultEntityId()).isEqualTo(10L);
        assertThat(operation.getResultSaveId()).isEqualTo(20L);
        assertThat(operation.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("보상 처리 중인 작업은 뒤늦게 COMPLETED로 전환할 수 없다")
    void cannotCompleteCompensatingOperation() {
        MongoCreateOperation operation = pendingOperation();
        operation.startCompensating("creation failed");

        assertThatThrownBy(() -> operation.complete(10L, 20L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("보상 삭제 성공과 최종 실패 상태를 구분한다")
    void finishesCompensation() {
        MongoCreateOperation compensated = pendingOperation();
        compensated.startCompensating("creation failed");
        compensated.markCompensated();

        MongoCreateOperation failed = pendingOperation("550e8400-e29b-41d4-a716-446655440001");
        failed.startCompensating("creation failed");
        failed.markCompensationFailed("mongo delete failed");

        assertThat(compensated.getStatus()).isEqualTo(MongoCreateOperationStatus.COMPENSATED);
        assertThat(failed.getStatus()).isEqualTo(MongoCreateOperationStatus.FAILED);
        assertThat(failed.getLastError()).isEqualTo("mongo delete failed");
    }

    private MongoCreateOperation pendingOperation() {
        return pendingOperation("550e8400-e29b-41d4-a716-446655440000");
    }

    private MongoCreateOperation pendingOperation(String operationId) {
        return MongoCreateOperation.pending(
                operationId,
                1L,
                MongoCreateOperationType.DOC,
                "request-hash",
                new MongoIdsDto(List.of("save-1"), List.of(), List.of())
        );
    }
}
