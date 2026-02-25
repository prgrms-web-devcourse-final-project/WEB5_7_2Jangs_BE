package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.app.create.BranchCreateOrchestrator;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @InjectMocks
    private BranchService branchService;

    @Mock
    private CommitQueryService commitQueryService;

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BranchQueryService branchQueryService;

    @Mock
    private BranchCreateOrchestrator branchCreateOrchestrator;

    @Test
    @DisplayName("leaf이면서 root이면서 save가 존재하는 상황에서 브랜치를 이미 있는 이름으로 생성 시 - BRANCH_NAME_DUPLICATED")
    void testAddSaveToExistingBranch() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("ignored", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);
        Save save = mock(Save.class);

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(branch);

        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);
        when(branch.getLeafCommit()).thenReturn(commit);
        when(branch.getRootCommit()).thenReturn(commit);
        when(branch.getName()).thenReturn("ignored");
        when(branch.getSave()).thenReturn(save);

        when(commitQueryService.getById(commitId)).thenReturn(commit);
        doNothing().when(docQueryService).checkByIdAndUserId(documentId, userId);
        doThrow(new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED))
                .when(branchQueryService).checkDuplicatedWithBranchName(documentId, "ignored");

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranchOrSave(documentId, request, userId));
        assertEquals(BranchErrorCode.BRANCH_NAME_DUPLICATED, ex.getErrorCode());
        verifyNoInteractions(branchCreateOrchestrator);
    }

    @Test
    @DisplayName("이어가기 - leaf + 다른 브랜치명인데 이름 중복이면 BRANCH_NAME_DUPLICATED")
    void continueWork_fail_whenLeafAndDifferentNameButDuplicated() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(branch);
        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);
        when(branch.getLeafCommit()).thenReturn(commit);
        when(branch.getName()).thenReturn("main");

        when(commitQueryService.getById(commitId)).thenReturn(commit);
        doNothing().when(docQueryService).checkByIdAndUserId(documentId, userId);
        doThrow(new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED))
                .when(branchQueryService).checkDuplicatedWithBranchName(documentId, "new-branch");

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranchOrSave(documentId, request, userId));
        assertEquals(BranchErrorCode.BRANCH_NAME_DUPLICATED, ex.getErrorCode());
        verifyNoInteractions(branchCreateOrchestrator);
    }

    @Test
    @DisplayName("이어가기 - fromBranch에 save가 있어도 새 브랜치 생성 경로는 정상 동작")
    void continueWork_success_whenFromBranchHasSaveAndCreateNewBranch() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(branch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");
        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);
        when(branch.getLeafCommit()).thenReturn(commit);
        when(branch.getName()).thenReturn("main");

        when(commitQueryService.getById(commitId)).thenReturn(commit);
        doNothing().when(docQueryService).checkByIdAndUserId(documentId, userId);
        doNothing().when(branchQueryService).checkDuplicatedWithBranchName(documentId, "new-branch");
        when(branchCreateOrchestrator.createBranchOrSave(any()))
                .thenReturn(new BranchCreateResponse(101L, 201L));

        // when
        BranchCreateResponse response = branchService.createBranchOrSave(documentId, request, userId);

        // then
        assertNotNull(response);
        assertEquals(101L, response.branchId());
        assertEquals(201L, response.saveId());
        verify(branchCreateOrchestrator).createBranchOrSave(any());
    }

    @Test
    @DisplayName("중간 커밋이면 새로운 브랜치 + 저장 추가")
    void testCreateNewBranchAndSave() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        User mockUser = mock(User.class);
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = Doc.builder().user(mockUser).title("title").build();
        ReflectionTestUtils.setField(doc, "id", 1L);

        Branch fromBranch = Branch.builder().doc(doc).name("from").build();
        Commit commit = mock(Commit.class);

        when(commit.getBranch()).thenReturn(fromBranch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");
        fromBranch.updateLeafCommit(commit);
        when(commitQueryService.getById(commitId)).thenReturn(commit);
        doNothing().when(docQueryService).checkByIdAndUserId(documentId, userId);
        doNothing().when(branchQueryService).checkDuplicatedWithBranchName(documentId, "new-branch");
        when(branchCreateOrchestrator.createBranchOrSave(any()))
                .thenReturn(new BranchCreateResponse(100L, 200L));

        // when
        BranchCreateResponse response =
                branchService.createBranchOrSave(documentId, request, userId);

        // then
        assertNotNull(response);
        assertEquals(100L, response.branchId());
        assertEquals(200L, response.saveId());
        verify(branchCreateOrchestrator).createBranchOrSave(any());
    }

    @Test
    @DisplayName("브랜치 이름 수정 성공")
    void renameBranch_success() {
        Long docId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        String newName = "수정된 이름";
        Commit commit = mock(Commit.class);

        Branch branch = Branch.builder().name("기존이름").doc(mock(Doc.class)).fromCommit(commit).build();

        doNothing().when(branchQueryService).checkBranchInDocOwnedByUser(docId, branchId, userId);
        when(branchQueryService.getById(branchId)).thenReturn(branch);

        BranchRenameResponse response =
                branchService.renameBranch(docId, branchId, newName, userId);

        assertEquals(newName, response.name());
    }

    @Test
    @DisplayName("브랜치가 문서에 없거나 유저 소유가 아니면 예외 발생")
    void renameBranch_branchOwnershipCheckFailed() {
        doThrow(new CustomException(BranchErrorCode.BRANCH_NOT_FOUND))
                .when(branchQueryService).checkBranchInDocOwnedByUser(anyLong(), anyLong(),
                        anyLong());

        CustomException e = assertThrows(CustomException.class,
                () -> branchService.renameBranch(1L, 2L, "new", 3L));
        assertEquals(BranchErrorCode.BRANCH_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("브랜치 삭제 성공")
    void deleteBranch_success() {
        // given
        Long documentId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        Commit commit = mock(Commit.class);

        Doc doc = mock(Doc.class);
        Branch branch = Branch.builder().name("dev").doc(doc).fromCommit(commit).build();

        Commit commit1 = Commit.builder().commitMongoId("seq1").branch(branch).build();
        Commit commit2 = Commit.builder().commitMongoId("seq2").branch(branch).build();
        ReflectionTestUtils.setField(commit1, "id", 10L);
        ReflectionTestUtils.setField(commit2, "id", 11L);
        ReflectionTestUtils.setField(branch, "commits", List.of(commit1, commit2));

        CommitBlockSequence seq1 =
                CommitBlockSequence.builder().blockOrders(List.of("block1", "block2")).build();

        CommitBlockSequence seq2 =
                CommitBlockSequence.builder().blockOrders(List.of("block3")).build();

        doNothing().when(branchQueryService)
                .checkBranchInDocOwnedByUser(documentId, branchId, userId);
        when(branchQueryService.getById(branchId)).thenReturn(branch);
        when(branchQueryService.existsSubBranchByFromCommitIds(any())).thenReturn(false);
        when(edgeRepository.findAllByPrevCommitIdInOrNextCommitIdIn(any(), any())).thenReturn(
                List.of()); // 빈 리스트로 가정

        when(commitBlockSequenceRepository.findById("seq1")).thenReturn(Optional.of(seq1));
        when(commitBlockSequenceRepository.findById("seq2")).thenReturn(Optional.of(seq2));

        // when
        branchService.deleteBranch(documentId, branchId, userId);

        // then - 브랜치 실제 삭제
        verify(branchQueryService).delete(branch);

        // 이벤트 발행 검증
        ArgumentCaptor<MongoIdsDto> captor = ArgumentCaptor.forClass(MongoIdsDto.class);
        verify(eventPublisher).publishEvent(captor.capture());

        MongoIdsDto emitted = captor.getValue();
        assertEquals(List.of("seq1", "seq2"), emitted.commitBlockSequenceIds());
        assertTrue(emitted.blockIds().containsAll(List.of("block1", "block2", "block3")));
        assertEquals(3, emitted.blockIds().size()); // block 중복 없이 수집되었는지도 검증
    }


    @Test
    @DisplayName("메인 브랜치 삭제 시도 시 예외 발생")
    void deleteBranch_mainBranch_fail() {
        Long docId = 1L;
        Long branchId = 2L;
        Long userId = 3L;

        Branch mainBranch = Branch.builder().name("main").doc(mock(Doc.class)).build();
        doNothing().when(branchQueryService).checkBranchInDocOwnedByUser(docId, branchId, userId);
        when(branchQueryService.getById(branchId)).thenReturn(mainBranch);

        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.deleteBranch(docId, branchId, userId));
        assertEquals(BranchErrorCode.MAIN_BRANCH_FIX_UNAVAILABLE, ex.getErrorCode());
    }

}
