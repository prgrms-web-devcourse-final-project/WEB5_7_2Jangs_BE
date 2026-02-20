package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import java.util.Optional;
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
    private BranchService branchService;

    @Mock
    private CommitCreateOrchestrator commitCreateOrchestrator;

    @Mock
    private BlockService blockService;

    @Mock
    private SaveService saveService;

    @Mock
    private EdgeService edgeService;

    @Mock
    private CommitContentAssembler assembler;

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
    private Commit leafCommit;
    private Commit fromCommit;
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
        leafCommit = org.mockito.Mockito.mock(Commit.class);
        fromCommit = org.mockito.Mockito.mock(Commit.class);
        createdCommit = org.mockito.Mockito.mock(Commit.class);
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit 기반으로 오케스트레이터 호출")
    void createCommit_success_withLeafCommit() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(branch.getLeafCommit()).thenReturn(leafCommit);
        when(leafCommit.getId()).thenReturn(11L);
        when(leafCommit.getCommitMongoId()).thenReturn("leaf-cbs-id");
        when(commitRepository.findById(11L)).thenReturn(Optional.of(leafCommit));
        when(createdCommit.getId()).thenReturn(101L);
        when(commitCreateOrchestrator.create(createCommitRequest, "leaf-cbs-id", doc, branch))
                .thenReturn(createdCommit);

        var result = commitService.createCommit(docId, createCommitRequest, userDetails.getId());

        assertThat(result.id()).isEqualTo(101L);
        verify(branchService).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());
        verify(commitCreateOrchestrator).create(createCommitRequest, "leaf-cbs-id", doc, branch);

        verifyNoInteractions(cbsRepository, blockService, saveService, edgeService);
    }

    @Test
    @DisplayName("커밋 생성 성공 - leafCommit이 null이면 fromCommit을 base로 사용")
    void createCommit_success_useFromCommitWhenLeafCommitIsNull() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(branch.getLeafCommit()).thenReturn(null);
        when(branch.getFromCommit()).thenReturn(fromCommit);
        when(fromCommit.getId()).thenReturn(21L);
        when(fromCommit.getCommitMongoId()).thenReturn("from-cbs-id");
        when(commitRepository.findById(21L)).thenReturn(Optional.of(fromCommit));
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
        when(branchService.getById(branchId)).thenReturn(branch);
        when(branch.getLeafCommit()).thenReturn(null);
        when(branch.getFromCommit()).thenReturn(null);
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
                .when(branchService).checkBranchInDocOwnedByUser(docId, branchId, userDetails.getId());

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService, never()).getById(docId);
        verify(branchService, never()).getById(branchId);
        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 브랜치 조회 실패 시 오케스트레이터 미호출")
    void createCommit_fail_whenBranchLookupFails() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        doThrow(new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND))
                .when(branchService).getById(branchId);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(commitRepository, commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - base commit 조회 중 repository 예외 전파")
    void createCommit_fail_whenBaseCommitLookupFails() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(branch.getLeafCommit()).thenReturn(leafCommit);
        when(leafCommit.getId()).thenReturn(11L);
        doThrow(new RuntimeException("repo fail"))
                .when(commitRepository).findById(11L);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("repo fail");

        verifyNoInteractions(commitCreateOrchestrator);
    }

    @Test
    @DisplayName("커밋 생성 실패 - 오케스트레이터 예외는 그대로 전파")
    void createCommit_fail_whenOrchestratorThrows() {
        when(docQueryService.getById(docId)).thenReturn(doc);
        when(branchService.getById(branchId)).thenReturn(branch);
        when(branch.getLeafCommit()).thenReturn(leafCommit);
        when(leafCommit.getId()).thenReturn(11L);
        when(commitRepository.findById(11L)).thenReturn(Optional.of(leafCommit));
        doThrow(new RuntimeException("orchestrator fail"))
                .when(commitCreateOrchestrator).create(createCommitRequest, null, doc, branch);

        assertThatThrownBy(() -> commitService.createCommit(docId, createCommitRequest, userDetails.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("orchestrator fail");
    }
}
