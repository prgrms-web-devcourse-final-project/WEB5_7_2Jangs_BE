package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitIntegrationTestUtils;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MergeCommitIntegrationTest {

    @Autowired
    private CommitService commitService;

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

    private User testUser;
    private Doc testDoc;
    private Branch baseBranch;
    private Branch targetBranch;
    private Commit baseCommit;
    private Commit targetCommit;
    private List<BlockDto> blockContent;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = CommitIntegrationTestUtils.createTestUser();
        userRepository.save(testUser);

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
        baseBranch.initializeRootCommitIfNull(baseCommit);

        // 타겟 커밋 생성
        targetCommit = CommitIntegrationTestUtils.createTestCommit(targetBranch, "Target commit");
        commitRepository.save(targetCommit);
        targetBranch.updateLeafCommit(targetCommit);
        targetBranch.initializeRootCommitIfNull(targetCommit);

        // 전문 생성
        blockContent = CommitIntegrationTestUtils.createTestBlockContent();
    }

    @Test
    @DisplayName("정상적인 병합 커밋 생성 테스트")
    void mergeCommit_Success() {
        // given
        MergeCommitRequest request =
                CommitIntegrationTestUtils.createMergeCommitRequest(baseBranch, targetBranch);

        // when
        CreateCommitResponse response = commitService.mergeCommit(testDoc.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // 병합 커밋이 생성되었는지 확인
        Commit mergeCommit = commitRepository.findById(response.id()).orElse(null);
        assertThat(mergeCommit).isNotNull();
        assertThat(mergeCommit.getTitle()).isEqualTo("Merge commit");
        assertThat(mergeCommit.getDescription()).isEqualTo("Merge feature into main");
        assertThat(mergeCommit.getCommitMongoId()).isNotNull();

        // 베이스 브랜치의 leafCommit이 업데이트되었는지 확인
        Branch updatedBaseBranch = branchRepository.findById(baseBranch.getId()).orElse(null);
        assertThat(updatedBaseBranch.getLeafCommit().getId()).isEqualTo(response.id());

        // 두 개의 간선이 생성되었는지 확인
        List<Edge> edges = edgeRepository.findByNextCommitId(response.id());

        assertThat(edges).hasSize(2);

        // 간선이 올바르게 생성되었는지 확인
        List<Long> prevCommitIds = edges.stream()
                .map(edge -> edge.getPrevCommit().getId())
                .toList();

        assertThat(prevCommitIds).containsExactlyInAnyOrder(
                baseCommit.getId(),
                targetCommit.getId()
        );
    }

    @Test
    @DisplayName("빈 컨텐츠로 병합 커밋 생성 테스트")
    void mergeCommit_EmptyContent_Success() {
        // given
        MergeCommitRequest request = new MergeCommitRequest(
                "Empty merge commit",
                "Merge with empty content",
                baseBranch.getId(),
                targetBranch.getId(),
                List.of()
        );

        // when
        CreateCommitResponse response = commitService.mergeCommit(testDoc.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        // 병합 커밋이 생성되었는지 확인
        Commit mergeCommit = commitRepository.findById(response.id()).orElse(null);
        assertThat(mergeCommit).isNotNull();
        assertThat(mergeCommit.getTitle()).isEqualTo("Empty merge commit");
    }

    @Test
    @DisplayName("존재하지 않는 문서 ID로 병합 시도 시 예외 발생")
    void mergeCommit_Document_NotFound() {
        // given
        Long nonExistentDocId = 999L;
        MergeCommitRequest request =
                CommitIntegrationTestUtils.createMergeCommitRequest(baseBranch, targetBranch);

        // when & then
        assertThatThrownBy(() -> commitService.mergeCommit(nonExistentDocId, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(DocErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 베이스 브랜치 ID로 병합 시도 시 예외 발생")
    void mergeCommit_BaseBranch_NotFound() {
        // given
        MergeCommitRequest request = new MergeCommitRequest(
                "Merge commit",
                "Merge feature into main",
                999L,   // 존재하지 않는 베이스 브랜치 ID
                targetBranch.getId(),
                blockContent
        );

        // when & then
        assertThatThrownBy(() -> commitService.mergeCommit(testDoc.getId(), request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(BranchErrorCode.BRANCH_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 타겟 브랜치 ID로 병합 시도 시 예외 발생")
    void mergeCommit_TargetBranch_NotFound() {
        // given
        MergeCommitRequest request = new MergeCommitRequest(
                "Merge commit",
                "Merge feature into main",
                baseBranch.getId(),
                999L,   // 존재하지 않는 타겟 브랜치 ID
                blockContent
        );

        // when & then
        assertThatThrownBy(() -> commitService.mergeCommit(testDoc.getId(), request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(BranchErrorCode.BRANCH_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("leafCommit이 없는 브랜치로 병합 시도 시 예외 발생")
    void mergeCommit_NoLeafCommit() {
        // given
        Branch branchWithoutLeaf = CommitIntegrationTestUtils.createTestBranch("no-leaf", testDoc);
        branchRepository.save(branchWithoutLeaf);

        MergeCommitRequest request = new MergeCommitRequest(
                "Merge commit",
                "Merge feature into main",
                branchWithoutLeaf.getId(),
                targetBranch.getId(),
                blockContent
        );

        // when & then
        assertThatThrownBy(() -> commitService.mergeCommit(testDoc.getId(), request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.COMMIT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("동일한 브랜치끼리 병합 시도 테스트")
    void mergeCommit_SameBranch_Fail() {
        // given
        MergeCommitRequest request = new MergeCommitRequest(
                "Self merge commit",
                "Merge branch into itself",
                baseBranch.getId(),
                baseBranch.getId(), // 동일한 브랜치
                blockContent
        );

        // when & then
        assertThatThrownBy(() -> commitService.mergeCommit(testDoc.getId(), request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.COMMIT_BAD_REQUEST.getMessage());
    }
}