package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BranchServiceTest {

    @InjectMocks
    private BranchService branchService;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SaveRepository saveRepository;

    @Mock
    private SaveContentRepository saveContentRepository;

    @Mock
    private CommitContentAssembler commitContentAssembler;

    @Mock
    private DocRepository docRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("leaf 커밋이면 기존 브랜치에 저장 추가")
    void testAddSaveToExistingBranch() {
        // given
        Long documentId = 1L;
        Long userId = 1L;
        Long commitId = 10L;
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

        when(docRepository.existsByIdAndUserId(documentId, userId)).thenReturn(true);

        Save save = Save.builder().branch(branch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent =
                SaveContent.builder().content(List.of(SaveBlock.from(Map.of("key", "value"))))
                        .build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response =
                branchService.createBranchOrSave(documentId, request, userId);

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
        Long userId = 1L;
        Long commitId = 10L;
        BranchCreateRequest request = new BranchCreateRequest("new-branch", commitId);

        Doc doc = Doc.builder().title("title").user(User.builder().build()).build();
        ReflectionTestUtils.setField(doc, "id", documentId);

        Branch fromBranch = Branch.builder().doc(doc).name("from").build();
        Commit commit = mock(Commit.class);

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(fromBranch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");

        when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
        when(branchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(docRepository.existsByIdAndUserId(documentId, userId)).thenReturn(true);

        Save save = Save.builder().branch(fromBranch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent =
                SaveContent.builder().content(List.of(SaveBlock.from(Map.of("block", "data"))))
                        .build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response =
                branchService.createBranchOrSave(documentId, request, userId);

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
}
