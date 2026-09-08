package io.ejangs.docsa.domain.merge.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.merge.app.MergeService;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitIntegrationTestUtils;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.edge.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MergeIntegrationTest {

    @Autowired
    private MergeService mergeService;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private SaveRepository saveRepository;

    @Autowired
    private SaveContentRepository saveContentRepository;

    private User testUser;
    private Doc testDoc;
    private Branch baseBranch;
    private Branch targetBranch;
    private Commit baseCommit;
    private Commit targetCommit;
    private List<Map<String, Object>> blockContent;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = CommitIntegrationTestUtils.createTestUser();
        userRepository.save(testUser);
        userDetails = CustomUserDetails.from(testUser);

        // 테스트 문서 생성
        testDoc = CommitIntegrationTestUtils.createTestDoc(testUser);
        docRepository.save(testDoc);

        // 베이스 브랜치 생성
        baseBranch = CommitIntegrationTestUtils.createTestBranch("main", testDoc);
        branchRepository.save(baseBranch);

        // 타겟 브랜치 생성
        targetBranch = CommitIntegrationTestUtils.createTestBranch("feature", testDoc);
        branchRepository.save(targetBranch);

        // 베이스 커밋 생성
        baseCommit = CommitIntegrationTestUtils.createTestCommit(baseBranch, "Base commit");
        commitRepository.save(baseCommit);
        baseBranch.updateLeafCommit(baseCommit);
        baseBranch.updateRootCommit(baseCommit);

        // 타겟 커밋 생성
        targetCommit = CommitIntegrationTestUtils.createTestCommit(targetBranch, "Target commit");
        commitRepository.save(targetCommit);
        targetBranch.updateLeafCommit(targetCommit);
        targetBranch.updateRootCommit(targetCommit);

        // 전문 생성
        blockContent = CommitIntegrationTestUtils.createTestBlockContent();
    }

    @Test
    @DisplayName("정상적인 머지 요청이면 새 브랜치와 작업장이 생성된다")
    void merge_success() {
        // given
        MergeRequest request =
                CommitIntegrationTestUtils.createMergeRequest(baseCommit, targetCommit);

        // when
        MergeResponse response =
                mergeService.merge(
                        testDoc.getId(), request, userDetails.getId(), UUID.randomUUID().toString());

        assertThat(response).isNotNull();
        assertThat(response.branchId()).isNotNull();
        assertThat(response.saveId()).isNotNull();

        Branch createdBranch = branchRepository.findById(response.branchId()).orElse(null);
        assertThat(createdBranch).isNotNull();
        assertThat(createdBranch.getName()).isEqualTo("merged-branch");
        assertThat(createdBranch.getDoc().getId()).isEqualTo(testDoc.getId());
        assertThat(createdBranch.getFromCommit().getId()).isEqualTo(baseCommit.getId());
        assertThat(createdBranch.getLeafCommit()).isNull();
        assertThat(createdBranch.getRootCommit()).isNull();
        assertThat(createdBranch.getMergeTargetCommit().getId()).isEqualTo(targetCommit.getId());

        Save createdSave = saveRepository.findById(response.saveId()).orElse(null);
        assertThat(createdSave).isNotNull();
        assertThat(createdSave.getBranch().getId()).isEqualTo(createdBranch.getId());
        assertThat(createdSave.getSaveMongoId()).isNotBlank();
        assertThat(saveContentRepository.findById(createdSave.getSaveMongoId())).isPresent();
    }

    @Test
    @DisplayName("빈 컨텐츠로 머지해도 새 브랜치와 작업장이 생성된다")
    void merge_emptyContent_success() {
        // given
        MergeRequest request = new MergeRequest(
                "merged-branch",
                baseCommit.getId(),
                targetCommit.getId(),
                List.of()
        );

        // when
        MergeResponse response =
                mergeService.merge(
                        testDoc.getId(), request, userDetails.getId(), UUID.randomUUID().toString());

        assertThat(response).isNotNull();
        Save createdSave = saveRepository.findById(response.saveId()).orElse(null);
        assertThat(createdSave).isNotNull();
        assertThat(saveContentRepository.findById(createdSave.getSaveMongoId())).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 문서 ID로 병합 시도 시 예외 발생")
    void merge_documentNotFound() {
        // given
        Long nonExistentDocId = 999L;
        MergeRequest request =
                CommitIntegrationTestUtils.createMergeRequest(baseCommit, targetCommit);

        // when & then
        assertThatThrownBy(
                () -> mergeService.merge(
                        nonExistentDocId, request, userDetails.getId(), UUID.randomUUID().toString())
        )
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(DocErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 기준 커밋 ID로 병합 시도 시 예외 발생")
    void merge_baseCommitNotFound() {
        // given
        MergeRequest request = new MergeRequest(
                "merged-branch",
                999L,   // 존재하지 않는 베이스 커밋 ID
                targetCommit.getId(),
                blockContent
        );

        // when & then
        assertThatThrownBy(
                () -> mergeService.merge(
                        testDoc.getId(), request, userDetails.getId(), UUID.randomUUID().toString())
        )
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.COMMIT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 비교 커밋 ID로 병합 시도 시 예외 발생")
    void merge_targetCommitNotFound() {
        // given
        MergeRequest request = new MergeRequest(
                "merged-branch",
                baseCommit.getId(),
                999L,   // 존재하지 않는 타겟 커밋 ID
                blockContent
        );

        // when & then
        assertThatThrownBy(
                () -> mergeService.merge(
                        testDoc.getId(), request, userDetails.getId(), UUID.randomUUID().toString())
        )
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.COMMIT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("동일한 커밋을 병합하면 INVALID_MERGE_REQUEST 예외가 발생한다")
    void merge_sameCommit_fail() {
        MergeRequest request = new MergeRequest(
                "merged-branch",
                baseCommit.getId(),
                baseCommit.getId(),
                blockContent
        );

        assertThatThrownBy(() -> mergeService.merge(
                testDoc.getId(), request, userDetails.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.INVALID_MERGE_REQUEST.getMessage());
    }
}
