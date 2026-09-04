package io.ejangs.docsa.domain.merge.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.merge.app.MergeMongoTxService;
import io.ejangs.docsa.domain.branch.merge.app.MergeMySqlTxService;
import io.ejangs.docsa.domain.branch.merge.app.MergeOrchestrator;
import io.ejangs.docsa.domain.branch.merge.app.MergeService.MergeContext;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
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
class MergeOrchestratorTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final MongoIdsDto PLAN = new MongoIdsDto(List.of("save-1"), List.of(), List.of());

    @Mock
    private MergeMongoTxService mongoTxService;
    @Mock
    private MergeMySqlTxService mySqlTxService;
    @Mock
    private MongoCreateOperationService operationService;
    @Mock
    private MongoCreateCompensationService compensationService;
    @InjectMocks
    private MergeOrchestrator orchestrator;

    @Test
    @DisplayName("PENDING 저장 뒤 사전 확정한 SaveContent ID로 병합 작업장을 생성한다")
    void mergeSuccess() {
        MergeContext context = context();
        MergeRequest request = request();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.MERGE, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createMongoPart(request.content(), "save-1")).thenReturn("save-1");
        when(mySqlTxService.createMySqlPart(context, request, "save-1", OPERATION_ID))
                .thenReturn(new MergeResponse(10L, 20L));

        MergeResponse result = orchestrator.merge(
                context, request, 1L, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(new MergeResponse(10L, 20L));
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("PENDING 저장이 실패하면 Mongo 생성을 시작하지 않는다")
    void doesNotCreateMongoWhenPendingFails() {
        MergeContext context = context();
        MergeRequest request = request();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.MERGE, "hash", PLAN))
                .thenThrow(new RuntimeException("mysql pending fail"));

        assertThatThrownBy(() -> orchestrator.merge(
                context, request, 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql pending fail");

        verify(mongoTxService, never()).createMongoPart(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(compensationService, never()).request(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("병합 MySQL 생성 실패 시 사전 기록한 Mongo 대상을 보상 요청한다")
    void requestsCompensationOnFailure() {
        MergeContext context = context();
        MergeRequest request = request();
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.MERGE, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(mongoTxService.createMongoPart(request.content(), "save-1")).thenReturn("save-1");
        when(mySqlTxService.createMySqlPart(context, request, "save-1", OPERATION_ID))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.merge(
                context, request, 1L, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(CustomException.class)
                .hasMessage(CommitErrorCode.FAIL_MERGE.getMessage());
        verify(compensationService).request(OPERATION_ID, "mysql fail");
    }

    private MergeContext context() {
        return new MergeContext(
                org.mockito.Mockito.mock(Commit.class),
                org.mockito.Mockito.mock(Commit.class),
                org.mockito.Mockito.mock(Doc.class)
        );
    }

    private MergeRequest request() {
        return new MergeRequest("merge", 1L, 2L, List.of());
    }
}
