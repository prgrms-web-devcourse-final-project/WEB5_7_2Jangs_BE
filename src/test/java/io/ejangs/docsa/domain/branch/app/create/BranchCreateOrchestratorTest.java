package io.ejangs.docsa.domain.branch.app.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.mongo.outbox.app.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchCreateOrchestratorTest {

    @Mock
    private BranchCreateMongoTxService branchCreateMongoTxService;

    @Mock
    private BranchCreateMySqlTxService branchCreateMySqlTxService;

    @Mock
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @InjectMocks
    private BranchCreateOrchestrator orchestrator;

    @Test
    @DisplayName("브랜치 생성 Saga 성공 - Mongo와 MySQL이 모두 성공하면 보상 Outbox를 생성하지 않는다")
    void create_success() {
        BranchCreateContext context = createContext("save-base-cbs");
        BranchCreateResponse expected = new BranchCreateResponse(10L, 20L);

        when(branchCreateMongoTxService.createSaveContentFromCommit("save-base-cbs"))
                .thenReturn("save-content-1");
        when(branchCreateMySqlTxService.createMySqlPart(context, "save-content-1"))
                .thenReturn(expected);

        BranchCreateResponse result = orchestrator.create(context);

        assertThat(result).isEqualTo(expected);
        verify(mongoDeleteOutboxFactory, never()).create(any(), any(), any(), any(String.class),
                any());
    }

    @Test
    @DisplayName("브랜치 생성 Saga 실패 - Mongo 성공 후 MySQL이 실패하면 SaveContent 보상 Outbox를 생성한다")
    void create_fail_whenMySqlFails_thenCompensateMongo() {
        BranchCreateContext context = createContext("save-base-cbs");

        when(branchCreateMongoTxService.createSaveContentFromCommit("save-base-cbs"))
                .thenReturn("save-content-1");
        when(branchCreateMySqlTxService.createMySqlPart(context, "save-content-1"))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql fail");

        verify(mongoDeleteOutboxFactory).create(
                eq(TriggerType.COMPENSATE),
                eq(DomainType.BRANCH),
                eq(OriginType.SAVE_CONTENT_ID),
                eq("save-content-1"),
                eq(new MongoIdsDto(java.util.List.of("save-content-1"), null, null))
        );
    }

    @Test
    @DisplayName("브랜치 생성 Saga 실패 - Mongo 단계가 실패하면 MySQL과 보상 Outbox를 호출하지 않는다")
    void create_fail_whenMongoFails_thenDoNotTouchMySqlOrOutbox() {
        BranchCreateContext context = createContext("save-base-cbs");

        when(branchCreateMongoTxService.createSaveContentFromCommit("save-base-cbs"))
                .thenThrow(new RuntimeException("mongo fail"));

        assertThatThrownBy(() -> orchestrator.create(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mongo fail");

        verify(branchCreateMySqlTxService, never()).createMySqlPart(any(), any());
        verify(mongoDeleteOutboxFactory, never()).create(any(), any(), any(), any(String.class),
                any());
    }

    private BranchCreateContext createContext(String fromCommitMongoId) {
        Doc doc = org.mockito.Mockito.mock(Doc.class);
        Branch fromBranch = org.mockito.Mockito.mock(Branch.class);
        Commit fromCommit = org.mockito.Mockito.mock(Commit.class);
        return new BranchCreateContext(doc, fromBranch, fromCommit, "feature", fromCommitMongoId);
    }
}
