package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.app.create.CommitMongoTxService;
import io.ejangs.docsa.domain.commit.app.create.CommitMySqlTxService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.app.MongoCreateCompensationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationStart;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitCreateOrchestratorTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final MongoIdsDto PLAN = new MongoIdsDto(
            List.of(), List.of("cbs-1"), List.of("block-1"));

    @Mock
    private CommitMySqlTxService mySqlTxService;
    @Mock
    private CommitMongoTxService mongoTxService;
    @Mock
    private MongoCreateOperationService operationService;
    @Mock
    private MongoCreateCompensationService compensationService;
    @InjectMocks
    private CommitCreateOrchestrator orchestrator;

    @Test
    @DisplayName("PENDING 저장 뒤 사전 확정한 CBS와 Block ID로 커밋을 생성한다")
    void createSuccess() {
        CreateCommitRequest request = request();
        Doc doc = org.mockito.Mockito.mock(Doc.class);
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        Commit commit = org.mockito.Mockito.mock(Commit.class);
        when(commit.getId()).thenReturn(10L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.COMMIT, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createMongoPart(request, "base-cbs", PLAN)).thenReturn(PLAN);
        when(mySqlTxService.createMySqlPart(doc, branch, request, "cbs-1", OPERATION_ID))
                .thenReturn(commit);

        CreateCommitResponse result = orchestrator.create(
                request, "base-cbs", doc, branch, 1L, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(new CreateCommitResponse(10L));
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("COMPLETED operationId 재요청은 기존 커밋 ID를 반환한다")
    void returnsCompletedResult() {
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.COMMIT, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.completed(10L, null));

        CreateCommitResponse result = orchestrator.create(
                request(), "base-cbs", org.mockito.Mockito.mock(Doc.class),
                org.mockito.Mockito.mock(Branch.class), 1L, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(new CreateCommitResponse(10L));
        verify(mongoTxService, never()).createMongoPart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("PENDING 저장이 실패하면 Mongo 생성을 시작하지 않는다")
    void doesNotCreateMongoWhenPendingFails() {
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.COMMIT, "hash", PLAN))
                .thenThrow(new RuntimeException("mysql pending fail"));

        assertThatThrownBy(() -> orchestrator.create(
                request(), "base-cbs", org.mockito.Mockito.mock(Doc.class),
                org.mockito.Mockito.mock(Branch.class), 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql pending fail");

        verify(mongoTxService, never()).createMongoPart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("최종 MySQL 생성 실패 시 사전 기록한 Mongo 대상을 보상 요청한다")
    void requestsCompensationOnFailure() {
        CreateCommitRequest request = request();
        Doc doc = org.mockito.Mockito.mock(Doc.class);
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.COMMIT, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createMongoPart(request, "base-cbs", PLAN)).thenReturn(PLAN);
        when(mySqlTxService.createMySqlPart(doc, branch, request, "cbs-1", OPERATION_ID))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create(
                request, "base-cbs", doc, branch, 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(CustomException.class)
                .hasMessage(CommitErrorCode.FAIL_CREATE_COMMIT.getMessage());
        verify(compensationService).request(OPERATION_ID, "mysql fail");
    }

    private CreateCommitRequest request() {
        return new CreateCommitRequest(
                "title", "description", 1L,
                List.of(java.util.Map.of("id", "editor-1")),
                List.of("editor-1")
        );
    }
}
