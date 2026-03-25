package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.app.create.CommitMongoTxService;
import io.ejangs.docsa.domain.commit.app.create.CommitMySqlTxService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.outbox.app.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitCreateOrchestratorTest {

    @Mock
    private CommitMySqlTxService commitMySqlTxService;

    @Mock
    private CommitMongoTxService commitMongoTxService;

    @Mock
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @InjectMocks
    private CommitCreateOrchestrator orchestrator;

    @Test
    @DisplayName("Saga 생성 성공 - Mongo 생성 후 MySQL 성공 시 보상 삭제를 호출하지 않는다")
    void create_success() {
        CreateCommitRequest request = new CreateCommitRequest("t", "d", 1L, Collections.emptyList(), Collections.emptyList());
        Doc doc = org.mockito.Mockito.mock(Doc.class);
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        Commit commit = org.mockito.Mockito.mock(Commit.class);
        MongoIdsDto ids = new MongoIdsDto(Collections.emptyList(), java.util.List.of("cbs-1"), Collections.emptyList());

        when(commitMongoTxService.createMongoPart(request, "base-cbs")).thenReturn(ids);
        when(commitMySqlTxService.createMySqlPart(doc, branch, request, "cbs-1")).thenReturn(commit);

        Commit result = orchestrator.create(request, "base-cbs", doc, branch);

        assertThat(result).isEqualTo(commit);
        verify(mongoDeleteOutboxFactory, never()).create(any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Saga 생성 실패 - MySQL 실패 시 Mongo 보상 삭제를 호출하고 예외를 유지한다")
    void create_fail_compensateMongo() {
        CreateCommitRequest request = new CreateCommitRequest("t", "d", 1L, Collections.emptyList(), Collections.emptyList());
        Doc doc = org.mockito.Mockito.mock(Doc.class);
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        MongoIdsDto ids = new MongoIdsDto(Collections.emptyList(), java.util.List.of("cbs-1"), Collections.emptyList());

        when(commitMongoTxService.createMongoPart(request, "base-cbs")).thenReturn(ids);
        when(commitMySqlTxService.createMySqlPart(doc, branch, request, "cbs-1"))
                .thenThrow(new RuntimeException("mysql fail"));

        assertThatThrownBy(() -> orchestrator.create(request, "base-cbs", doc, branch))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mysql fail");

        verify(mongoDeleteOutboxFactory).create(
                eq(TriggerType.COMPENSATE),
                eq(DomainType.COMMIT),
                eq(OriginType.CBS_ID),
                eq("cbs-1"),
                eq(ids)
        );
    }
}
