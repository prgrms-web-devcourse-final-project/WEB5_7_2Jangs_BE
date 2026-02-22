package io.ejangs.docsa.domain.doc.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.DocCreateMySqlTxService;
import io.ejangs.docsa.domain.doc.app.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocCreateOrchestratorTest {

    @Mock
    private SaveService saveService;

    @Mock
    private DocCreateMySqlTxService docCreateMySqlTxService;

    @Mock
    private MongoDeleteRetryService mongoDeleteRetryService;

    @InjectMocks
    private DocCreateOrchestrator orchestrator;

    @Test
    @DisplayName("문서 생성 Saga 성공 - MySQL 성공 시 Mongo 보상 삭제를 호출하지 않는다")
    void create_success() {
        User user = org.mockito.Mockito.mock(User.class);
        SaveContent saved = SaveContent.builder().build();
        ReflectionTestUtils.setField(saved, "id", "save-1");
        DocCreateResponse expected = new DocCreateResponse(10L, 20L);

        when(saveService.createSaveContent()).thenReturn(saved);
        when(docCreateMySqlTxService.createMySqlPart("doc", user, "save-1")).thenReturn(expected);

        DocCreateResponse result = orchestrator.create("doc", user);

        assertThat(result).isEqualTo(expected);
        verify(mongoDeleteRetryService, never()).deleteMongoData(any());
    }

    @Test
    @DisplayName("문서 생성 Saga 실패 - MySQL 실패 시 SaveContent 보상 삭제를 호출하고 예외를 유지한다")
    void create_fail_compensateMongo() {
        User user = org.mockito.Mockito.mock(User.class);
        SaveContent saved = SaveContent.builder().build();
        ReflectionTestUtils.setField(saved, "id", "save-1");

        when(saveService.createSaveContent()).thenReturn(saved);
        when(docCreateMySqlTxService.createMySqlPart("doc", user, "save-1"))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create("doc", user))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql fail");

        verify(mongoDeleteRetryService).deleteMongoData(any());
    }
}
