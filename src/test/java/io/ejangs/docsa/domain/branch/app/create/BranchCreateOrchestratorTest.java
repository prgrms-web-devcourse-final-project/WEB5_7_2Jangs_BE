package io.ejangs.docsa.domain.branch.app.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
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
class BranchCreateOrchestratorTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final MongoIdsDto PLAN = new MongoIdsDto(List.of("save-1"), List.of(), List.of());

    @Mock
    private BranchCreateMongoTxService mongoTxService;
    @Mock
    private BranchCreateMySqlTxService mySqlTxService;
    @Mock
    private MongoCreateOperationService operationService;
    @Mock
    private MongoCreateCompensationService compensationService;
    @InjectMocks
    private BranchCreateOrchestrator orchestrator;

    @Test
    @DisplayName("PENDING 저장 뒤 사전 확정한 SaveContent ID로 브랜치를 생성한다")
    void createSuccess() {
        BranchCreateContext context = createContext();
        BranchCreateResponse expected = new BranchCreateResponse(10L, 20L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.BRANCH, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createSaveContentFromCommit("base-cbs", "save-1"))
                .thenReturn("save-1");
        when(mySqlTxService.createMySqlPart(context, "save-1", OPERATION_ID)).thenReturn(expected);

        BranchCreateResponse result = orchestrator.create(context, 1L, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(expected);
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("COMPLETED operationId 재요청은 기존 브랜치 결과를 반환한다")
    void returnsCompletedResult() {
        BranchCreateContext context = createContext();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.BRANCH, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.completed(10L, 20L));

        BranchCreateResponse result = orchestrator.create(context, 1L, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(new BranchCreateResponse(10L, 20L));
        verify(mongoTxService, never()).createSaveContentFromCommit(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("PENDING 저장이 실패하면 Mongo 생성을 시작하지 않는다")
    void doesNotCreateMongoWhenPendingFails() {
        BranchCreateContext context = createContext();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.BRANCH, "hash", PLAN))
                .thenThrow(new RuntimeException("mysql pending fail"));

        assertThatThrownBy(() -> orchestrator.create(context, 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql pending fail");

        verify(mongoTxService, never()).createSaveContentFromCommit(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Mongo 또는 최종 MySQL 단계가 실패하면 생성 작업 보상을 요청한다")
    void requestsCompensationOnFailure() {
        BranchCreateContext context = createContext();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.BRANCH, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createSaveContentFromCommit("base-cbs", "save-1"))
                .thenReturn("save-1");
        when(mySqlTxService.createMySqlPart(context, "save-1", OPERATION_ID))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create(context, 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(CustomException.class)
                .hasMessage(BranchErrorCode.FAIL_CREATE_BRANCH.getMessage());
        verify(compensationService).request(OPERATION_ID, "mysql fail");
    }

    private BranchCreateContext createContext() {
        return new BranchCreateContext(
                org.mockito.Mockito.mock(Doc.class),
                org.mockito.Mockito.mock(Branch.class),
                org.mockito.Mockito.mock(Commit.class),
                "feature",
                "base-cbs"
        );
    }
}
