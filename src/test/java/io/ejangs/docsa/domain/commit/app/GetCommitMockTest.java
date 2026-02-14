package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCommitMockTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private BranchService branchService;

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private CommitContentAssembler assembler;

    @InjectMocks
    private CommitService commitService;

    private User user;
    private Doc doc;
    private Branch branch;
    private Commit baseCommit;
    private Commit targetCommit;
    private List<Map<String, Object>> mockContent;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        // User 생성
        user = CommitMockTestUtils.createUser();
        userDetails = CustomUserDetails.from(user);

        // Doc 생성
        doc = CommitMockTestUtils.createDoc(user);

        // Branch 생성
        branch = CommitMockTestUtils.createBranch(doc, baseCommit);

        // BaseCommit 생성
        baseCommit = CommitMockTestUtils.createBaseCommit(branch);
        baseCommit.setBranch(branch);

        targetCommit = CommitMockTestUtils.createMockCommit(branch, 2L);
        mockContent = CommitMockTestUtils.createMockContent();
    }

    @Test
    @DisplayName("getCommit - 정상적으로 커밋을 조회한다")
    void getCommit_Success() {
        // given
        Long docId = 1L;
        Long commitId = 1L;
        String commitMongoId = "mongo-commit-id";

        given(commitRepository.findById(commitId)).willReturn(Optional.of(targetCommit));
        given(assembler.assemble(commitMongoId)).willReturn(mockContent);

        // when
        CommitResponse response = commitService.getCommit(docId, commitId, userDetails.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(mockContent);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository).findById(commitId);
        verify(assembler).assemble(commitMongoId);
    }

    @Test
    @DisplayName("getCommit - 존재하지 않는 커밋 조회")
    void getCommit_Commit_NotFound() {
        // given
        Long docId = 1L;
        Long commitId = 999L;

        given(commitRepository.findById(commitId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commitService.getCommit(docId, commitId, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository).findById(commitId);
        verify(assembler, never()).assemble(any());
    }

    @Test
    @DisplayName("getCommit - 문서가 존재하지 않을 때")
    void getCommit_Doc_NotFound() {
        // given
        Long docId = 999L;
        Long commitId = 1L;

        doThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND))
                .when(docQueryService).checkByIdAndUserId(docId, userDetails.getId());

        // when & then
        assertThatThrownBy(() -> commitService.getCommit(docId, commitId, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository, never()).findById(any());
        verify(assembler, never()).assemble(any());
    }

    @Test
    @DisplayName("compareCommitForMerge - 정상적으로 두 커밋을 조회한다")
    void compareCommitForMerge_Success() {
        // given
        Long docId = 1L;
        Long baseId = 1L;
        Long targetId = 2L;
        String baseCommitMongoId = "base-mongo-commit-id";
        String targetCommitMongoId = "mongo-commit-id";

        List<Map<String, Object>> baseContent = CommitMockTestUtils.createMockContent();
        List<Map<String, Object>> targetContent = CommitMockTestUtils.createMockContent();

        given(commitRepository.findById(baseId)).willReturn(Optional.of(baseCommit));
        given(commitRepository.findById(targetId)).willReturn(Optional.of(targetCommit));
        given(assembler.assemble(baseCommitMongoId)).willReturn(baseContent);
        given(assembler.assemble(targetCommitMongoId)).willReturn(targetContent);

        // when
        CompareMergeCommitResponse response = commitService.compareCommitForMerge(docId, baseId,
                targetId, userDetails.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.base()).isEqualTo(baseContent);
        assertThat(response.target()).isEqualTo(targetContent);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository).findById(baseId);
        verify(commitRepository).findById(targetId);
        verify(assembler).assemble(baseCommitMongoId);
        verify(assembler).assemble(targetCommitMongoId);
    }

    @Test
    @DisplayName("compareCommitForMerge - 베이스 커밋이 존재하지 않을 때")
    void compareCommitForMerge_BaseCommit_NotFound() {
        // given
        Long docId = 1L;
        Long baseId = 999L;
        Long targetId = 2L;

        given(commitRepository.findById(baseId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> commitService.compareCommitForMerge(docId, baseId, targetId,
                userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository).findById(baseId);
        verify(commitRepository, never()).findById(targetId);
        verify(assembler, never()).assemble(any());
    }

    @Test
    @DisplayName("compareCommitForMerge - 타겟 커밋이 존재하지 않을 때")
    void compareCommitForMerge_TargetCommit_NotFound() {
        // given
        Long docId = 1L;
        Long baseId = 1L;
        Long targetId = 999L;
        String baseCommitMongoId = "base-mongo-commit-id";

        List<Map<String, Object>> baseContent = CommitMockTestUtils.createMockContent();

        given(commitRepository.findById(baseId)).willReturn(Optional.of(baseCommit));
        given(commitRepository.findById(targetId)).willReturn(Optional.empty());
        given(assembler.assemble(baseCommitMongoId)).willReturn(baseContent);

        // when & then
        assertThatThrownBy(() -> commitService.compareCommitForMerge(docId, baseId, targetId,
                userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository).findById(baseId);
        verify(commitRepository).findById(targetId);
        verify(assembler).assemble(baseCommitMongoId);
    }

    @Test
    @DisplayName("compareCommitForMerge - 문서가 존재하지 않을 때")
    void compareCommitForMerge_Doc_NotFound() {
        // given
        Long docId = 999L;
        Long baseId = 1L;
        Long targetId = 2L;

        doThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND))
                .when(docQueryService).checkByIdAndUserId(docId, userDetails.getId());

        // when & then
        assertThatThrownBy(() -> commitService.compareCommitForMerge(docId, baseId, targetId,
                userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docQueryService).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitRepository, never()).findById(any());
        verify(assembler, never()).assemble(any());
    }
}