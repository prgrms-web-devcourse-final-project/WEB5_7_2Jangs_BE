package io.ejangs.docsa.domain.doc.app.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.create.DocCreateMySqlTxService;
import io.ejangs.docsa.domain.doc.app.create.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.app.SaveWriter;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
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
class DocCreateOrchestratorTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final MongoIdsDto PLAN = new MongoIdsDto(List.of("save-1"), List.of(), List.of());

    @Mock
    private SaveWriter saveWriter;

    @Mock
    private DocCreateMySqlTxService docCreateMySqlTxService;

    @Mock
    private MongoCreateOperationService operationService;

    @Mock
    private MongoCreateCompensationService compensationService;

    @InjectMocks
    private DocCreateOrchestrator orchestrator;

    @Test
    @DisplayName("PENDING 저장 뒤 사전 확정한 ID로 Mongo를 생성하고 MySQL과 COMPLETED를 커밋한다")
    void createSuccess() {
        User user = org.mockito.Mockito.mock(User.class);
        DocCreateResponse expected = new DocCreateResponse(10L, 20L);
        when(user.getId()).thenReturn(1L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.DOC, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(docCreateMySqlTxService.createMySqlPart("doc", user, "save-1", OPERATION_ID))
                .thenReturn(expected);

        DocCreateResponse result = orchestrator.create("doc", user, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(expected);
        verify(saveWriter).insertSaveContent("save-1");
        verify(compensationService, never()).request(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("COMPLETED operationId 재요청은 Mongo와 MySQL을 다시 생성하지 않고 기존 결과를 반환한다")
    void returnsCompletedResult() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.DOC, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.completed(10L, 20L));

        DocCreateResponse result = orchestrator.create("doc", user, OPERATION_ID, "hash", PLAN);

        assertThat(result).isEqualTo(new DocCreateResponse(10L, 20L));
        verify(saveWriter, never()).insertSaveContent(org.mockito.ArgumentMatchers.anyString());
        verify(docCreateMySqlTxService, never()).createMySqlPart(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("PENDING 저장이 실패하면 Mongo 생성을 시작하지 않는다")
    void doesNotCreateMongoWhenPendingFails() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.DOC, "hash", PLAN))
                .thenThrow(new RuntimeException("mysql pending fail"));

        assertThatThrownBy(() -> orchestrator.create("doc", user, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql pending fail");

        verify(saveWriter, never()).insertSaveContent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Mongo 또는 최종 MySQL 생성이 실패하면 사전 기록한 작업의 보상을 요청한다")
    void requestsCompensationWhenCreationFails() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(operationService.start(OPERATION_ID, 1L, MongoCreateOperationType.DOC, "hash", PLAN))
                .thenReturn(MongoCreateOperationStart.newOperation());
        when(docCreateMySqlTxService.createMySqlPart("doc", user, "save-1", OPERATION_ID))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create("doc", user, OPERATION_ID, "hash", PLAN))
                .isInstanceOf(CustomException.class)
                .hasMessage(DocErrorCode.FAIL_CREATE_DOCUMENT.getMessage());

        verify(compensationService).request(OPERATION_ID, "mysql fail");
    }
}
