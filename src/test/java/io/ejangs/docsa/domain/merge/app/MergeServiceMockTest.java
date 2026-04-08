package io.ejangs.docsa.domain.merge.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.merge.app.MergeOrchestrator;
import io.ejangs.docsa.domain.branch.merge.app.MergeService;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MergeServiceMockTest {

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private BranchQueryService branchQueryService;

    @Mock
    private CommitQueryService commitQueryService;

    @Mock
    private CommitContentAssembler commitContentAssembler;

    @Mock
    private MergeOrchestrator mergeOrchestrator;

    @InjectMocks
    private MergeService mergeService;

    private Long docId;
    private Long userId;
    private Doc doc;
    private Commit baseCommit;
    private Commit targetCommit;

    @BeforeEach
    void setUp() {
        docId = 1L;
        User user = CommitMockTestUtils.createUser();
        userId = user.getId();
        doc = CommitMockTestUtils.createDoc(user);
        baseCommit = org.mockito.Mockito.mock(Commit.class);
        targetCommit = org.mockito.Mockito.mock(Commit.class);
    }

    @Test
    @DisplayName("merge는 같은 브랜치 요청도 허용하고 오케스트레이터까지 전달한다")
    void merge_success_whenBaseAndTargetAreSameBranch() {
        Long baseCommitId = 11L;
        Long targetCommitId = 22L;

        when(docQueryService.getByIdAndUserId(docId, userId)).thenReturn(doc);
        when(baseCommit.getId()).thenReturn(baseCommitId);
        when(targetCommit.getId()).thenReturn(targetCommitId);
        when(commitQueryService.getById(baseCommitId)).thenReturn(baseCommit);
        when(commitQueryService.getById(targetCommitId)).thenReturn(targetCommit);
        when(mergeOrchestrator.merge(eq(doc), eq(baseCommit), any(MergeRequest.class)))
                .thenReturn(new MergeResponse(999L, 1001L));

        MergeRequest request = new MergeRequest(
                "merged-branch",
                baseCommitId,
                targetCommitId,
                Collections.emptyList()
        );

        var response = mergeService.merge(docId, request, userId);

        assertThat(response.branchId()).isEqualTo(999L);
        assertThat(response.saveId()).isEqualTo(1001L);
        verify(branchQueryService).checkDuplicatedWithBranchName(docId, "merged-branch");
        verify(commitQueryService)
                .checkTwoCommitsInDocOwnedByUser(baseCommitId, targetCommitId, docId, userId);
        verify(mergeOrchestrator).merge(doc, baseCommit, request);
    }

    @Test
    @DisplayName("merge 검증 실패 - 중복 브랜치 이름이면 오케스트레이터를 호출하지 않는다")
    void merge_fail_whenBranchNameDuplicated() {
        Long baseCommitId = 31L;
        Long targetCommitId = 32L;
        when(docQueryService.getByIdAndUserId(docId, userId)).thenReturn(doc);
        doThrow(new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED))
                .when(branchQueryService).checkDuplicatedWithBranchName(docId, "merged-branch");

        MergeRequest request = new MergeRequest(
                "merged-branch",
                baseCommitId,
                targetCommitId,
                Collections.emptyList()
        );

        assertThatThrownBy(() -> mergeService.merge(docId, request, userId))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(commitQueryService);
        verifyNoInteractions(mergeOrchestrator);
    }

    @Test
    @DisplayName("merge 검증 실패 - 동일한 커밋을 병합하면 예외")
    void merge_fail_whenBaseAndTargetAreSameCommit() {
        Long sameCommitId = 41L;

        when(docQueryService.getByIdAndUserId(docId, userId)).thenReturn(doc);
        when(baseCommit.getId()).thenReturn(sameCommitId);
        when(commitQueryService.getById(sameCommitId)).thenReturn(baseCommit);
        doThrow(new CustomException(CommitErrorCode.INVALID_MERGE_REQUEST))
                .when(commitQueryService)
                .checkTwoCommitsInDocOwnedByUser(sameCommitId, sameCommitId, docId, userId);

        MergeRequest request = new MergeRequest(
                "merged-branch",
                sameCommitId,
                sameCommitId,
                Collections.emptyList()
        );

        assertThatThrownBy(() -> mergeService.merge(docId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("동일한 커밋을 병합할 수 없습니다.");

        verify(commitQueryService).checkTwoCommitsInDocOwnedByUser(
                sameCommitId, sameCommitId, docId, userId
        );
        verifyNoInteractions(mergeOrchestrator);
    }

    @Test
    @DisplayName("merge 검증 실패 - 문서 조회 실패 시 오케스트레이터를 호출하지 않는다")
    void merge_fail_whenDocLookupFails() {
        MergeRequest request = new MergeRequest(
                "merged-branch",
                51L,
                52L,
                Collections.emptyList()
        );

        doThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND))
                .when(docQueryService).getByIdAndUserId(docId, userId);

        assertThatThrownBy(() -> mergeService.merge(docId, request, userId))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(mergeOrchestrator);
    }
}
