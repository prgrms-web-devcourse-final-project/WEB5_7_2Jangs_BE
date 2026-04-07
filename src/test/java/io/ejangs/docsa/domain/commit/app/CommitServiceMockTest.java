package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.app.merge.MergeOrchestrator;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.mongo.outbox.util.MongoIdsCollector;
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

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitBlockSequenceRepository cbsRepository;

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private CommitQueryService commitQueryService;

    @Mock
    private BranchService branchService;

    @Mock
    private BranchQueryService branchQueryService;

    @Mock
    private CommitCreateOrchestrator commitCreateOrchestrator;

    @Mock
    private MergeOrchestrator mergeOrchestrator;

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
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit 기반으로 오케스트레이터 호출")
    void createCommit_success_withLeafCommit() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        when(commitQueryService.resolveBaseCommitCbsMongoId(branch)).thenReturn("leaf-cbs-id");
        when(createdCommit.getId()).thenReturn(101L);
        when(commitCreateOrchestrator.create(createCommitRequest, "leaf-cbs-id", doc, branch))
                .thenReturn(createdCommit);

        var result = commitService.createCommit(docId, createCommitRequest, userDetails.getId());

        assertThat(result.id()).isEqualTo(101L);
        verify(branchQueryService).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());
        verify(commitCreateOrchestrator).create(createCommitRequest, "leaf-cbs-id", doc, branch);

        verifyNoInteractions(cbsRepository, blockService, saveService, edgeService, commitRepository);
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit이 null이면 fromCommit을 base로 사용")
    void createCommit_success_useFromCommitWhenLeafCommitIsNull() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        when(commitQueryService.resolveBaseCommitCbsMongoId(branch)).thenReturn("from-cbs-id");
        when(createdCommit.getId()).thenReturn(101L);
        when(commitCreateOrchestrator.create(createCommitRequest, "from-cbs-id", doc, branch))
                .thenReturn(createdCommit);

        var result = commitService.createCommit(docId, createCommitRequest, userDetails.getId());

        assertThat(result.id()).isEqualTo(101L);
        verify(commitCreateOrchestrator).create(createCommitRequest, "from-cbs-id", doc, branch);
    }

    @Test
    @DisplayName("커밋 생성 성공 - 최초 커밋이면 baseCommitCbsMongoId는 null")
    void createCommit_success_initialCommit_baseIsNull() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        when(commitQueryService.resolveBaseCommitCbsMongoId(branch)).thenReturn(null);
        when(createdCommit.getId()).thenReturn(101L);
        when(commitCreateOrchestrator.create(createCommitRequest, null, doc, branch))
                .thenReturn(createdCommit);

        var result = commitService.createCommit(docId, createCommitRequest, userDetails.getId());

        assertThat(result.id()).isEqualTo(101L);
        verify(commitCreateOrchestrator).create(createCommitRequest, null, doc, branch);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 브랜치 권한 검증 실패 시 오케스트레이터 미호출")
    void createCommit_fail_branchOwnershipCheck() {
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(branchQueryService).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService, never()).getById(docId);
        verify(branchQueryService, never()).getById(branchId);
        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 브랜치 조회 실패 시 오케스트레이터 미호출")
    void createCommit_fail_whenBranchLookupFails() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(branchQueryService).getById(branchId);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(commitRepository, commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - base commit 조회 중 repository 예외 전파")
    void createCommit_fail_whenBaseCommitLookupFails() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        doThrow(new RuntimeException("repo fail"))
                .when(commitQueryService).resolveBaseCommitCbsMongoId(branch);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("repo fail");

        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 오케스트레이터 예외는 그대로 전파")
    void createCommit_fail_whenOrchestratorThrows() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        when(commitQueryService.resolveBaseCommitCbsMongoId(branch)).thenReturn("base-cbs-id");
        doThrow(new RuntimeException("orchestrator fail"))
                .when(commitCreateOrchestrator).create(createCommitRequest, "base-cbs-id", doc, branch);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("orchestrator fail");
    }

    @Test
    @DisplayName("merge는 같은 브랜치 요청도 허용하고 오케스트레이터까지 전달한다")
    void mergeCommit_success_whenBaseAndTargetAreSameBranch() {
        Long baseCommitId = 11L;
        Long targetCommitId = 22L;

        Branch sameBranch = org.mockito.Mockito.mock(Branch.class);
        Commit baseCommit = org.mockito.Mockito.mock(Commit.class);
        Commit targetCommit = org.mockito.Mockito.mock(Commit.class);
        Commit mergedCommit = org.mockito.Mockito.mock(Commit.class);

        when(baseCommit.getId()).thenReturn(baseCommitId);
        when(targetCommit.getId()).thenReturn(targetCommitId);
        when(baseCommit.getBranch()).thenReturn(sameBranch);
        when(targetCommit.getBranch()).thenReturn(sameBranch);
        when(sameBranch.getId()).thenReturn(100L);
        when(sameBranch.getLeafCommit()).thenReturn(baseCommit, targetCommit);
        when(mergedCommit.getId()).thenReturn(999L);

        when(commitQueryService.getById(baseCommitId)).thenReturn(baseCommit);
        when(commitQueryService.getById(targetCommitId)).thenReturn(targetCommit);
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(mergeOrchestrator.merge(eq(doc), eq(sameBranch), eq(sameBranch), any(MergeCommitRequest.class)))
                .thenReturn(mergedCommit);

        MergeCommitRequest request = new MergeCommitRequest(
                "merged-branch",
                "merge",
                "same branch",
                baseCommitId,
                targetCommitId,
                Collections.emptyList()
        );

        var response = commitService.mergeCommit(docId, request, userDetails.getId());

        assertThat(response.id()).isEqualTo(999L);
        verify(branchQueryService, times(2))
                .checkBranchInDocOwnedByUser(docId, 100L, userDetails.getId());
        verify(mergeOrchestrator).merge(doc, sameBranch, sameBranch, request);
    }

    @Test
    @DisplayName("merge 권한 검증 실패 - base/target 중 하나라도 권한이 없으면 예외")
    void mergeCommit_fail_whenAnyBranchOwnershipCheckFails() {
        Long baseCommitId = 31L;
        Long targetCommitId = 32L;

        Branch baseBranch = org.mockito.Mockito.mock(Branch.class);
        Branch targetBranch = org.mockito.Mockito.mock(Branch.class);
        Commit baseCommit = org.mockito.Mockito.mock(Commit.class);
        Commit targetCommit = org.mockito.Mockito.mock(Commit.class);

        when(baseCommit.getId()).thenReturn(baseCommitId);
        when(targetCommit.getId()).thenReturn(targetCommitId);
        when(baseCommit.getBranch()).thenReturn(baseBranch);
        when(targetCommit.getBranch()).thenReturn(targetBranch);
        when(baseBranch.getId()).thenReturn(201L);
        when(targetBranch.getId()).thenReturn(202L);
        when(baseBranch.getLeafCommit()).thenReturn(baseCommit);
        when(targetBranch.getLeafCommit()).thenReturn(targetCommit);

        when(commitQueryService.getById(baseCommitId)).thenReturn(baseCommit);
        when(commitQueryService.getById(targetCommitId)).thenReturn(targetCommit);

        // baseBranch 권한 체크는 통과
        org.mockito.Mockito.doNothing()
                .when(branchQueryService).checkBranchInDocOwnedByUser(docId, 201L, userDetails.getId());
        // targetBranch 권한 체크에서 실패
        doThrow(new CustomException(BranchErrorCode.BRANCH_NOT_FOUND_OR_FORBIDDEN))
                .when(branchQueryService).checkBranchInDocOwnedByUser(docId, 202L, userDetails.getId());

        MergeCommitRequest request = new MergeCommitRequest(
                "merged-branch",
                "merge",
                "ownership fail",
                baseCommitId,
                targetCommitId,
                Collections.emptyList()
        );

        assertThatThrownBy(() -> commitService.mergeCommit(docId, request, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(branchQueryService).checkBranchInDocOwnedByUser(docId, 201L, userDetails.getId());
        verify(branchQueryService).checkBranchInDocOwnedByUser(docId, 202L, userDetails.getId());
        verifyNoInteractions(mergeOrchestrator);
    }
}
