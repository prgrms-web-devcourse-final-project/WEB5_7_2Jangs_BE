package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.app.create.BranchCreateOrchestrator;
import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitReader;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreatePlanFactory;
import io.ejangs.docsa.global.saga.create.app.MongoCreateRequestHasher;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    private static final MongoIdsDto CREATE_PLAN = new MongoIdsDto(
            List.of("save-1"), List.of(), List.of());

    @InjectMocks
    private BranchService branchService;

    @Mock
    private CommitReader commitReader;

    @Mock
    private DocReader docReader;

    @Mock
    private EdgeService edgeService;

    @Mock
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Mock
    private BranchReader branchReader;

    @Mock
    private BranchWriter branchWriter;

    @Mock
    private BranchCreateOrchestrator branchCreateOrchestrator;

    @Mock
    private MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    @Mock
    private MongoCreateOperationService mongoCreateOperationService;

    @Mock
    private MongoCreatePlanFactory mongoCreatePlanFactory;

    @Mock
    private MongoCreateRequestHasher mongoCreateRequestHasher;

    @BeforeEach
    void setUpCreateSaga() {
        lenient().when(mongoCreateRequestHasher.hash(any())).thenReturn("hash");
        lenient().when(mongoCreatePlanFactory.singleSaveContent()).thenReturn(CREATE_PLAN);
    }

    @Test
    @DisplayName("새 브랜치 이름이 중복되면 BRANCH_NAME_DUPLICATED")
    void createBranch_fail_whenDifferentNameButDuplicated() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getBranch()).thenReturn(branch);
        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);

        when(commitReader.getById(commitId)).thenReturn(commit);
        doNothing().when(docReader).checkByIdAndUserId(documentId, userId);
        doThrow(new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED))
                .when(branchReader).checkDuplicatedWithBranchName(documentId, "new-branch");

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranch(documentId, request, userId, operationId()));
        assertEquals(BranchErrorCode.BRANCH_NAME_DUPLICATED, ex.getErrorCode());
        verifyNoInteractions(branchCreateOrchestrator);
    }

    @Test
    @DisplayName("다른 이름으로 이어서 작업을 시작하면 항상 새 브랜치와 저장을 생성한다")
    void createBranch_success_whenDifferentNameIsRequested() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getBranch()).thenReturn(branch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");
        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);

        when(commitReader.getById(commitId)).thenReturn(commit);
        doNothing().when(docReader).checkByIdAndUserId(documentId, userId);
        doNothing().when(branchReader).checkDuplicatedWithBranchName(documentId, "new-branch");
        when(branchCreateOrchestrator.create(any(), eq(userId), anyString(), eq("hash"), eq(CREATE_PLAN)))
                .thenReturn(new BranchCreateResponse(101L, 201L));

        // when
        BranchCreateResponse response = branchService.createBranch(
                documentId, request, userId, operationId());

        // then
        assertNotNull(response);
        assertEquals(101L, response.branchId());
        assertEquals(201L, response.saveId());
        ArgumentCaptor<BranchCreateContext> contextCaptor = ArgumentCaptor.forClass(
                BranchCreateContext.class);
        verify(branchCreateOrchestrator).create(
                contextCaptor.capture(), eq(userId), anyString(), eq("hash"), eq(CREATE_PLAN));

        BranchCreateContext context = contextCaptor.getValue();
        assertSame(doc, context.doc());
        assertSame(branch, context.fromBranch());
        assertSame(commit, context.fromCommit());
        assertEquals("new-branch", context.branchName());
        assertEquals("mongo-1", context.fromCommitMongoId());
    }

    @Test
    @DisplayName("중간 커밋에서 이어서 작업을 시작해도 새 브랜치와 저장을 생성한다")
    void createBranch_success_fromIntermediateCommit() {
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
        when(commitReader.getById(commitId)).thenReturn(commit);
        doNothing().when(docReader).checkByIdAndUserId(documentId, userId);
        doNothing().when(branchReader).checkDuplicatedWithBranchName(documentId, "new-branch");
        when(branchCreateOrchestrator.create(any(), eq(userId), anyString(), eq("hash"), eq(CREATE_PLAN)))
                .thenReturn(new BranchCreateResponse(100L, 200L));

        // when
        BranchCreateResponse response =
                branchService.createBranch(documentId, request, userId, operationId());

        // then
        assertNotNull(response);
        assertEquals(100L, response.branchId());
        assertEquals(200L, response.saveId());
        ArgumentCaptor<BranchCreateContext> contextCaptor = ArgumentCaptor.forClass(
                BranchCreateContext.class);
        verify(branchCreateOrchestrator).create(
                contextCaptor.capture(), eq(userId), anyString(), eq("hash"), eq(CREATE_PLAN));

        BranchCreateContext context = contextCaptor.getValue();
        assertSame(doc, context.doc());
        assertSame(fromBranch, context.fromBranch());
        assertSame(commit, context.fromCommit());
        assertEquals("new-branch", context.branchName());
        assertEquals("mongo-1", context.fromCommitMongoId());
    }

    @Test
    @DisplayName("다른 문서의 커밋으로 브랜치를 만들려고 하면 COMMIT_NOT_IN_DOCUMENT")
    void createBranch_fail_whenCommitBelongsToAnotherDocument() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc commitDoc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getBranch()).thenReturn(branch);
        when(branch.getDoc()).thenReturn(commitDoc);
        when(commitDoc.getId()).thenReturn(999L);

        when(commitReader.getById(commitId)).thenReturn(commit);
        doNothing().when(docReader).checkByIdAndUserId(documentId, userId);

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranch(documentId, request, userId, operationId()));
        assertEquals(DocErrorCode.COMMIT_NOT_IN_DOCUMENT, ex.getErrorCode());
        verify(branchReader, never()).checkDuplicatedWithBranchName(anyLong(), anyString());
        verifyNoInteractions(branchCreateOrchestrator);
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

        doNothing().when(branchReader).checkBranchInDocOwnedByUser(docId, branchId, userId);
        when(branchReader.getById(branchId)).thenReturn(branch);

        BranchRenameResponse response =
                branchService.renameBranch(docId, branchId, newName, userId);

        assertEquals(newName, response.name());
    }

    @Test
    @DisplayName("브랜치가 문서에 없거나 유저 소유가 아니면 예외 발생")
    void renameBranch_branchOwnershipCheckFailed() {
        doThrow(new CustomException(BranchErrorCode.BRANCH_NOT_FOUND))
                .when(branchReader).checkBranchInDocOwnedByUser(anyLong(), anyLong(),
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

        doNothing().when(branchReader)
                .checkBranchInDocOwnedByUser(documentId, branchId, userId);
        when(branchReader.getById(branchId)).thenReturn(branch);
        when(branchReader.existsSubBranchByFromCommitIds(any())).thenReturn(false);
        doNothing().when(edgeService).deleteEdgesConnectedToCommits(any());

        when(commitBlockSequenceRepository.findById("seq1")).thenReturn(Optional.of(seq1));
        when(commitBlockSequenceRepository.findById("seq2")).thenReturn(Optional.of(seq2));

        // when
        branchService.deleteBranch(documentId, branchId, userId);

        // then - 브랜치 실제 삭제
        verify(branchWriter).delete(branch);

        // Outbox 적재 검증
        ArgumentCaptor<MongoIdsDto> captor = ArgumentCaptor.forClass(MongoIdsDto.class);
        verify(mongoDeleteJobEnqueuer).enqueueBranchDeletion(
                eq(branchId),
                captor.capture()
        );

        MongoIdsDto emitted = captor.getValue();
        assertEquals(List.of("seq1", "seq2"), emitted.commitBlockSequenceIds());
        assertTrue(emitted.blockIds().containsAll(List.of("block1", "block2", "block3")));
        assertEquals(3, emitted.blockIds().size()); // block 중복 없이 수집되었는지도 검증
    }

    private static String operationId() {
        return UUID.randomUUID().toString();
    }


    @Test
    @DisplayName("메인 브랜치 삭제 시도 시 예외 발생")
    void deleteBranch_mainBranch_fail() {
        Long docId = 1L;
        Long branchId = 2L;
        Long userId = 3L;

        Branch mainBranch = Branch.builder().name("main").doc(mock(Doc.class)).build();
        doNothing().when(branchReader).checkBranchInDocOwnedByUser(docId, branchId, userId);
        when(branchReader.getById(branchId)).thenReturn(mainBranch);

        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.deleteBranch(docId, branchId, userId));
        assertEquals(BranchErrorCode.MAIN_BRANCH_FIX_UNAVAILABLE, ex.getErrorCode());
    }

}
