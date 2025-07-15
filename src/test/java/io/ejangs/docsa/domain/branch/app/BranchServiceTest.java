package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
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

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("fromCommitId가 null이면 예외 발생")
    void testThrowWhenFromCommitIdIsNull() {
        // given
        BranchCreateRequest request = new BranchCreateRequest("test-branch", null);

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranchOrSave(1L, request));
        assertEquals(CommitErrorCode.INVALID_FROM_COMMIT, ex.getErrorCode());
    }

    @Test
    @DisplayName("leaf 커밋이면 기존 브랜치에 저장 추가")
    void testAddSaveToExistingBranch() {
        // given
        Long documentId = 1L;
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

        Save save = Save.builder().branch(branch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent = SaveContent.builder()
                .content(List.of(SaveBlock.from(Map.of("key", "value"))))
                .build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response = branchService.createBranchOrSave(documentId, request);

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

        when(commit.getId()).thenReturn(commitId);
        when(commit.getBranch()).thenReturn(fromBranch);
        when(commit.getCommitMongoId()).thenReturn("mongo-1");
        when(mockUser.getDocs()).thenReturn(new ArrayList<>());

        when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
        when(branchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Save save = Save.builder().branch(fromBranch).build();
        when(saveRepository.save(any())).thenReturn(save);
        when(commitContentAssembler.assemble("mongo-1")).thenReturn(
                List.of(Map.of("block", "data")));

        SaveContent saveContent = SaveContent.builder().content(List.of(
                SaveBlock.from(Map.of("block", "data")))).build();
        when(saveContentRepository.save(any())).thenReturn(saveContent);

        // when
        BranchCreateResponse response = branchService.createBranchOrSave(documentId, request);

        // then
        assertNotNull(response);
        verify(branchRepository).save(any());
        verify(saveRepository).save(any());
    }
}
