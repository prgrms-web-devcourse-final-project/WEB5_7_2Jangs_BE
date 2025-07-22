package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @InjectMocks
    private BranchService branchService;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DocRepository docRepository;

    @Mock
    private SaveRepository saveRepository;

    @Mock
    private SaveContentRepository saveContentRepository;

    @Mock
    private CommitContentAssembler commitContentAssembler;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(branchService, "defaultBranchName", "main");
    }

    @Test
    @DisplayName("fromCommitId가 null이면 예외 발생")
    void testThrowWhenFromCommitIdIsNull() {
        // given
        Long docId = 1L;
        Long userId = 1L;
        BranchCreateRequest request = new BranchCreateRequest("test-branch", null);

        // 문서가 존재하는 것으로 가정해야 INVALID_FROM_COMMIT 예외를 검증 가능
        when(docRepository.existsByIdAndUserId(docId, userId)).thenReturn(true);

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranchOrSave(docId, request, userId));
        assertEquals(CommitErrorCode.INVALID_FROM_COMMIT, ex.getErrorCode());
    }

    @Test
    @DisplayName("leaf 커밋이면 기존 브랜치에 저장 추가")
    void testAddSaveToExistingBranch() {
        // given
        Long documentId = 1L;
        Long commitId = 10L;
        User mockUser = mock(User.class);
        BranchCreateRequest request = new BranchCreateRequest("ignored", commitId);

        Doc doc = mock(Doc.class);
        Branch branch = mock(Branch.class);
        Commit commit = mock(Commit.class);

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(branch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");

        when(branch.getDoc()).thenReturn(doc);
        when(doc.getId()).thenReturn(documentId);
        when(branch.getLeafCommit()).thenReturn(commit);

        when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

        when(docRepository.existsByIdAndUserId(documentId, mockUser.getId())).thenReturn(true);

        Save save = Save.builder().branch(branch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent =
                SaveContent.builder().content(List.of(Map.of("key", "value"))).build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response =
                branchService.createBranchOrSave(documentId, request, mockUser.getId());

        // then
        assertNotNull(response);
        verify(saveRepository).save(any());
        verify(saveContentRepository).save(any());
        verify(commitContentAssembler).assemble("mongo-1");
    }

    @Test
    @DisplayName("중간 커밋이면 새로운 브랜치 + 저장 추가")
    void testCreateNewBranchAndSave() {
        // given
        Long documentId = 1L;
        Long commitId = 10L;
        User mockUser = mock(User.class);
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = Doc.builder().user(mockUser).title("title").build();
        ReflectionTestUtils.setField(doc, "id", 1L);

        Branch fromBranch = Branch.builder().doc(doc).name("from").build();
        Commit commit = mock(Commit.class);

        when(commit.getBranch()).thenReturn(fromBranch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");

        when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
        when(branchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(docRepository.existsByIdAndUserId(documentId, mockUser.getId())).thenReturn(true);

        Save save = Save.builder().branch(fromBranch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent =
                SaveContent.builder().content(List.of(Map.of("block", "data"))).build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response =
                branchService.createBranchOrSave(documentId, request, mockUser.getId());

        // then
        assertNotNull(response);
        verify(branchRepository).save(any());
        verify(saveRepository).save(any());
    }

    @Test
    @DisplayName("브랜치 이름 수정 성공")
    void renameBranch_success() {
        Long docId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        String newName = "수정된 이름";

        Branch branch = Branch.builder().name("기존이름").doc(mock(Doc.class)).fromCommit(null).build();
        when(branchRepository.existsByIdAndDocIdAndDocUserId(branchId, docId, userId)).thenReturn(
                true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        BranchRenameResponse response =
                branchService.renameBranch(docId, branchId, newName, userId);

        assertEquals(newName, response.name());
    }

    @Test
    @DisplayName("브랜치가 문서에 없거나 유저 소유가 아니면 예외 발생")
    void renameBranch_branchOwnershipCheckFailed() {
        when(branchRepository.existsByIdAndDocIdAndDocUserId(anyLong(), anyLong(),
                anyLong())).thenReturn(false);

        CustomException e = assertThrows(CustomException.class,
                () -> branchService.renameBranch(1L, 2L, "new", 3L));
        assertEquals(BranchErrorCode.BRANCH_NOT_FOUND_OR_FORBIDDEN, e.getErrorCode());
    }

    @Test
    @DisplayName("브랜치 삭제 성공")
    void deleteBranch_success() {
        // given
        Long documentId = 1L;
        Long branchId = 2L;
        Long userId = 3L;

        Doc doc = mock(Doc.class);
        Branch branch = Branch.builder().name("dev").doc(doc).build();

        Commit commit1 = Commit.builder().commitMongoId("seq1").branch(branch).build();
        Commit commit2 = Commit.builder().commitMongoId("seq2").branch(branch).build();
        ReflectionTestUtils.setField(commit1, "id", 10L);
        ReflectionTestUtils.setField(commit2, "id", 11L);
        ReflectionTestUtils.setField(branch, "commits", List.of(commit1, commit2));

        CommitBlockSequence seq1 =
                CommitBlockSequence.builder().blockOrders(List.of("block1", "block2")).build();

        CommitBlockSequence seq2 =
                CommitBlockSequence.builder().blockOrders(List.of("block3")).build();

        when(branchRepository.existsByIdAndDocIdAndDocUserId(branchId, documentId,
                userId)).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(branchRepository.existsByFromCommitIdIn(any())).thenReturn(false);
        when(edgeRepository.findAllByPrevCommitIdInOrNextCommitIdIn(any(), any())).thenReturn(List.of()); // 빈 리스트로 가정

        when(commitBlockSequenceRepository.findById("seq1")).thenReturn(Optional.of(seq1));
        when(commitBlockSequenceRepository.findById("seq2")).thenReturn(Optional.of(seq2));

        // when
        branchService.deleteBranch(documentId, branchId, userId);

        // then - 브랜치 실제 삭제
        verify(branchRepository).delete(branch);

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
        when(branchRepository.existsByIdAndDocIdAndDocUserId(branchId, docId, userId)).thenReturn(
                true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(mainBranch));

        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.deleteBranch(docId, branchId, userId));
        assertEquals(BranchErrorCode.MAIN_BRANCH_DELETE_UNAVAILABLE, ex.getErrorCode());
    }

}
