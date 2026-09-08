package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.branch.app.BranchReader;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.outbox.mongo.util.MongoIdsCollector;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreatePlanFactory;
import io.ejangs.docsa.global.saga.create.app.MongoCreateRequestHasher;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CommitServiceMockTest {

    private static final String OPERATION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final MongoIdsDto PLAN = new MongoIdsDto(
            List.of(), List.of("cbs-1"), List.of());

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitBlockSequenceRepository cbsRepository;

    @Mock
    private DocReader docReader;

    @Mock
    private CommitReader commitReader;

    @Mock
    private CommitWriter commitWriter;

    @Mock
    private BranchReader branchReader;

    @Mock
    private CommitCreateOrchestrator commitCreateOrchestrator;

    @Mock
    private BlockService blockService;

    @Mock
    private SaveService saveService;

    @Mock
    private EdgeService edgeService;

    @Mock
    private MongoIdsCollector mongoIdsCollector;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MongoCreateOperationService mongoCreateOperationService;

    @Mock
    private MongoCreatePlanFactory mongoCreatePlanFactory;

    @Mock
    private MongoCreateRequestHasher mongoCreateRequestHasher;

    @InjectMocks
    private CommitService commitService;

    private Long docId;
    private Long branchId;
    private CreateCommitRequest createCommitRequest;
    private User user;
    private Doc doc;
    private Branch branch;
    private Commit createdCommit;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        docId = 1L;
        branchId = 1L;

        createCommitRequest = new CreateCommitRequest(
                "Test commit",
                "",
                branchId,
                Collections.emptyList(),
                Collections.emptyList()
        );

        user = CommitMockTestUtils.createUser();
        userDetails = CustomUserDetails.from(user);
        doc = CommitMockTestUtils.createDoc(user);

        branch = org.mockito.Mockito.mock(Branch.class);
        createdCommit = org.mockito.Mockito.mock(Commit.class);
        org.mockito.Mockito.lenient().when(mongoCreateRequestHasher.hash(org.mockito.ArgumentMatchers.any()))
                .thenReturn("hash");
        org.mockito.Mockito.lenient().when(mongoCreatePlanFactory.commit(0)).thenReturn(PLAN);
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit 기반으로 오케스트레이터 호출")
    void createCommit_success_withLeafCommit() {
        when(docReader.getById(docId)).thenReturn(doc);
        when(branchReader.getById(branchId)).thenReturn(branch);
        when(commitReader.resolveBaseCommitCbsMongoId(branch)).thenReturn("leaf-cbs-id");
        when(commitCreateOrchestrator.create(
                createCommitRequest, "leaf-cbs-id", doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN))
                .thenReturn(new CreateCommitResponse(101L));

        var result = commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID);

        assertThat(result.id()).isEqualTo(101L);
        verify(branchReader).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());
        verify(commitCreateOrchestrator).create(
                createCommitRequest, "leaf-cbs-id", doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN);

        verifyNoInteractions(cbsRepository, blockService, saveService, edgeService, commitRepository);
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit이 null이면 fromCommit을 base로 사용")
    void createCommit_success_useFromCommitWhenLeafCommitIsNull() {
        when(docReader.getById(docId)).thenReturn(doc);
        when(branchReader.getById(branchId)).thenReturn(branch);
        when(commitReader.resolveBaseCommitCbsMongoId(branch)).thenReturn("from-cbs-id");
        when(commitCreateOrchestrator.create(
                createCommitRequest, "from-cbs-id", doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN))
                .thenReturn(new CreateCommitResponse(101L));

        var result = commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID);

        assertThat(result.id()).isEqualTo(101L);
        verify(commitCreateOrchestrator).create(
                createCommitRequest, "from-cbs-id", doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN);
    }

    @Test
    @DisplayName("커밋 생성 성공 - 최초 커밋이면 baseCommitCbsMongoId는 null")
    void createCommit_success_initialCommit_baseIsNull() {
        when(docReader.getById(docId)).thenReturn(doc);
        when(branchReader.getById(branchId)).thenReturn(branch);
        when(commitReader.resolveBaseCommitCbsMongoId(branch)).thenReturn(null);
        when(commitCreateOrchestrator.create(
                createCommitRequest, null, doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN))
                .thenReturn(new CreateCommitResponse(101L));

        var result = commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID);

        assertThat(result.id()).isEqualTo(101L);
        verify(commitCreateOrchestrator).create(
                createCommitRequest, null, doc, branch,
                userDetails.getId(), OPERATION_ID, "hash", PLAN);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 브랜치 권한 검증 실패 시 오케스트레이터 미호출")
    void createCommit_fail_branchOwnershipCheck() {
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(branchReader).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());

        assertThatThrownBy(() -> commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID))
                .isInstanceOf(CustomException.class);

        verify(docReader, never()).getById(docId);
        verify(branchReader, never()).getById(branchId);
        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 브랜치 조회 실패 시 오케스트레이터 미호출")
    void createCommit_fail_whenBranchLookupFails() {
        when(docReader.getById(docId)).thenReturn(doc);
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(branchReader).getById(branchId);

        assertThatThrownBy(() -> commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(commitRepository, commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - base commit 조회 중 repository 예외 전파")
    void createCommit_fail_whenBaseCommitLookupFails() {
        when(docReader.getById(docId)).thenReturn(doc);
        when(branchReader.getById(branchId)).thenReturn(branch);
        doThrow(new RuntimeException("repo fail"))
                .when(commitReader).resolveBaseCommitCbsMongoId(branch);

        assertThatThrownBy(() -> commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("repo fail");

        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 오케스트레이터 예외는 그대로 전파")
    void createCommit_fail_whenOrchestratorThrows() {
        when(docReader.getById(docId)).thenReturn(doc);
        when(branchReader.getById(branchId)).thenReturn(branch);
        when(commitReader.resolveBaseCommitCbsMongoId(branch)).thenReturn("base-cbs-id");
        doThrow(new RuntimeException("orchestrator fail"))
                .when(commitCreateOrchestrator).create(
                        createCommitRequest, "base-cbs-id", doc, branch,
                        userDetails.getId(), OPERATION_ID, "hash", PLAN);

        assertThatThrownBy(() -> commitService.createCommit(
                docId, createCommitRequest, userDetails.getId(), OPERATION_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("orchestrator fail");
    }

}
