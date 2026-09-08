package io.ejangs.docsa.domain.branch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BranchCreateIntegrationTest {

    @Autowired
    private BranchService branchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private SaveRepository saveRepository;

    @Autowired
    private SaveContentRepository saveContentRepository;

    @Autowired
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Autowired
    private BlockRepository blockRepository;

    private final Set<String> createdSaveContentIds = new HashSet<>();
    private final Set<String> createdSequenceIds = new HashSet<>();
    private final Set<String> createdBlockIds = new HashSet<>();

    @AfterEach
    void cleanupMongoArtifacts() {
        if (!createdSaveContentIds.isEmpty()) {
            saveContentRepository.deleteAllById(createdSaveContentIds);
            createdSaveContentIds.clear();
        }
        if (!createdSequenceIds.isEmpty()) {
            commitBlockSequenceRepository.deleteAllById(createdSequenceIds);
            createdSequenceIds.clear();
        }
        if (!createdBlockIds.isEmpty()) {
            blockRepository.deleteAllById(createdBlockIds);
            createdBlockIds.clear();
        }
    }

    @Test
    @DisplayName("같은 이름으로 브랜치를 만들려고 하면 BRANCH_NAME_DUPLICATED")
    void continueWork_fail_whenLeafRootHasSave_andSameName() {
        Fixture fixture = createSingleCommitFixture(unique("main"));
        BranchCreateRequest request = new BranchCreateRequest(fixture.branch().getName(),
                fixture.commit().getId());

        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranch(fixture.doc().getId(), request,
                        fixture.user().getId(), UUID.randomUUID().toString()));

        assertThat(ex.getErrorCode()).isEqualTo(BranchErrorCode.BRANCH_NAME_DUPLICATED);
        assertThat(saveRepository.findByBranchId(fixture.branch().getId())).isPresent();
    }

    @Test
    @DisplayName("다른 이름으로 브랜치를 만들면 기존 save가 있어도 새 브랜치와 save를 생성한다")
    void continueWork_success_whenLeafRootHasSave_andDifferentName() {
        Fixture fixture = createSingleCommitFixture(unique("main"));
        String newBranchName = unique("feature");

        BranchCreateResponse response = branchService.createBranch(
                fixture.doc().getId(),
                new BranchCreateRequest(newBranchName, fixture.commit().getId()),
                fixture.user().getId(),
                UUID.randomUUID().toString());

        assertThat(response).isNotNull();
        assertThat(response.branchId()).isNotEqualTo(fixture.branch().getId());
        assertThat(response.saveId()).isNotNull();

        Branch createdBranch = branchRepository.findById(response.branchId()).orElseThrow();
        assertThat(createdBranch.getName()).isEqualTo(newBranchName);
        assertThat(createdBranch.getFromCommit()).isNotNull();
        assertThat(createdBranch.getFromCommit().getId()).isEqualTo(fixture.commit().getId());

        Save createdSave = saveRepository.findById(response.saveId()).orElseThrow();
        assertThat(createdSave.getBranch().getId()).isEqualTo(createdBranch.getId());
        createdSaveContentIds.add(createdSave.getSaveMongoId());
    }

    @Test
    @DisplayName("새 브랜치 이름이 문서 내에서 중복되면 BRANCH_NAME_DUPLICATED")
    void continueWork_fail_whenCreateNewBranchPath_andNameDuplicated() {
        Fixture fixture = createSingleCommitFixture(unique("main"));
        String duplicatedName = unique("dup");

        branchRepository.saveAndFlush(
                Branch.builder().name(duplicatedName).doc(fixture.doc()).fromCommit(fixture.commit())
                        .build());

        CustomException ex = assertThrows(CustomException.class,
                () -> branchService.createBranch(
                        fixture.doc().getId(),
                        new BranchCreateRequest(duplicatedName, fixture.commit().getId()),
                        fixture.user().getId(),
                        UUID.randomUUID().toString()));

        assertThat(ex.getErrorCode()).isEqualTo(BranchErrorCode.BRANCH_NAME_DUPLICATED);
    }

    private Fixture createSingleCommitFixture(String branchName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = userRepository.saveAndFlush(User.builder()
                .email("branch-it-" + suffix + "@example.com")
                .password("pw")
                .name("user-" + suffix)
                .build());

        Doc doc = docRepository.saveAndFlush(Doc.builder()
                .title("doc-" + suffix)
                .user(user)
                .build());

        Branch branch = branchRepository.saveAndFlush(Branch.builder()
                .name(branchName)
                .doc(doc)
                .build());

        Block block = blockRepository.save(Block.builder()
                .content(Map.of("text", "base-" + suffix))
                .build());
        createdBlockIds.add(block.getId());

        CommitBlockSequence sequence = commitBlockSequenceRepository.save(
                CommitBlockSequence.builder()
                        .blockOrders(List.of(block.getId()))
                        .build());
        createdSequenceIds.add(sequence.getId());

        Commit commit = commitRepository.saveAndFlush(Commit.builder()
                .title("commit-" + suffix)
                .description("desc")
                .branch(branch)
                .commitMongoId(sequence.getId())
                .build());

        branch.updateRootCommit(commit);
        branch.updateLeafCommit(commit);
        branch = branchRepository.saveAndFlush(branch);

        SaveContent initialSaveContent = saveContentRepository.save(
                SaveContent.builder()
                        .content(List.of(Map.of("text", "save-" + suffix)))
                        .build());
        createdSaveContentIds.add(initialSaveContent.getId());

        saveRepository.saveAndFlush(
                Save.builder().branch(branch).saveMongoId(initialSaveContent.getId()).build());

        return new Fixture(user, doc, branch, commit);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(User user, Doc doc, Branch branch, Commit commit) {
    }
}
