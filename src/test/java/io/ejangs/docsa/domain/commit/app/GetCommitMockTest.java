package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMockTestUtils;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.util.List;
import java.util.Map;
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
    private CommitReader commitReader;

    @Mock
    private CommitWriter commitWriter;

    @Mock
    private DocReader docReader;

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

        given(commitReader.getById(commitId)).willReturn(targetCommit);
        given(assembler.assemble(commitMongoId)).willReturn(mockContent);

        // when
        CommitResponse response = commitService.getCommit(docId, commitId, userDetails.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(mockContent);

        verify(docReader).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitReader).getById(commitId);
        verify(assembler).assemble(commitMongoId);
    }

    @Test
    @DisplayName("getCommit - 존재하지 않는 커밋 조회")
    void getCommit_Commit_NotFound() {
        // given
        Long docId = 1L;
        Long commitId = 999L;

        given(commitReader.getById(commitId))
                .willThrow(new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> commitService.getCommit(docId, commitId, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docReader).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitReader).getById(commitId);
        verify(assembler, never()).assemble(any());
    }

    @Test
    @DisplayName("getCommit - 문서가 존재하지 않을 때")
    void getCommit_Doc_NotFound() {
        // given
        Long docId = 999L;
        Long commitId = 1L;

        doThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND))
                .when(docReader).checkByIdAndUserId(docId, userDetails.getId());

        // when & then
        assertThatThrownBy(() -> commitService.getCommit(docId, commitId, userDetails.getId()))
                .isInstanceOf(CustomException.class);

        verify(docReader).checkByIdAndUserId(docId, userDetails.getId());
        verify(commitReader, never()).getById(any());
        verify(assembler, never()).assemble(any());
    }

}
