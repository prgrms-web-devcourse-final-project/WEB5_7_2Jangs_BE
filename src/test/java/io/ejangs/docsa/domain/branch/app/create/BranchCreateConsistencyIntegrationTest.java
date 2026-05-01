package io.ejangs.docsa.domain.branch.app.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
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
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.TriggerType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BranchCreateConsistencyIntegrationTest {

    @Autowired
    private BranchCreateOrchestrator branchCreateOrchestrator;

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

    @Autowired
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private BranchCreateMongoTxService branchCreateMongoTxService;

    @MockitoSpyBean
    private BranchCreateMySqlTxService branchCreateMySqlTxService;

    private final Set<String> createdSequenceIds = new HashSet<>();
    private final Set<String> createdBlockIds = new HashSet<>();

    @AfterEach
    void cleanup() {
        reset(branchCreateMongoTxService, branchCreateMySqlTxService);
        mongoDeleteOutboxRepository.deleteAll();
        saveRepository.deleteAll();
        branchRepository.deleteAll();
        commitRepository.deleteAll();
        docRepository.deleteAll();
        userRepository.deleteAll();

        if (!createdSequenceIds.isEmpty()) {
            commitBlockSequenceRepository.deleteAllById(createdSequenceIds);
            createdSequenceIds.clear();
        }
        if (!createdBlockIds.isEmpty()) {
            blockRepository.deleteAllById(createdBlockIds);
            createdBlockIds.clear();
        }
        saveContentRepository.deleteAll();
    }

    @Test
    @DisplayName("브랜치 생성이 성공하면 Mongo SaveContent와 MySQL Branch/Save가 함께 생성되고 보상 Outbox는 남지 않는다")
    void createBranch_success_persistsMongoAndMySqlWithoutCompensateOutbox() {
        Fixture fixture = createSingleCommitFixture(unique("main"));
        Set<String> beforeSaveContentIds = currentSaveContentIds();

        BranchCreateResponse response = branchCreateOrchestrator.create(new BranchCreateContext(
                fixture.doc(),
                fixture.branch(),
                fixture.commit(),
                unique("feature"),
                fixture.commit().getCommitMongoId()
        ));

        Save createdSave = saveRepository.findById(response.saveId()).orElseThrow();

        assertThat(branchRepository.findById(response.branchId())).isPresent();
        assertThat(createdSave.getSaveMongoId()).isNotBlank();
        assertThat(saveContentRepository.findById(createdSave.getSaveMongoId())).isPresent();
        assertThat(currentSaveContentIds()).hasSize(beforeSaveContentIds.size() + 1);
        assertThat(mongoDeleteOutboxRepository.findAll()).isEmpty();
    }

    @Nested
    @DisplayName("Mongo 성공 후 MySQL 실패")
    class MySqlFailureCompensationTest {

        @Test
        @DisplayName("Branch/Save는 롤백되고 SaveContent 보상 Outbox가 생성된다")
        void createBranch_mysqlFailure_createsCompensateOutboxAndKeepsSaveContent() {
            Fixture fixture = createSingleCommitFixture(unique("main"));
            long beforeBranchCount = branchRepository.count();
            long beforeSaveCount = saveRepository.count();
            Set<String> beforeSaveContentIds = currentSaveContentIds();

            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new RuntimeException("force mysql rollback after persist");
            }).when(branchCreateMySqlTxService).createMySqlPart(any(), anyString());

            assertThatThrownBy(() -> branchCreateOrchestrator.create(new BranchCreateContext(
                    fixture.doc(),
                    fixture.branch(),
                    fixture.commit(),
                    unique("feature"),
                    fixture.commit().getCommitMongoId()
            ))).isInstanceOf(CustomException.class)
                    .hasMessage(BranchErrorCode.FAIL_CREATE_BRANCH.getMessage());

            Set<String> afterSaveContentIds = currentSaveContentIds();
            afterSaveContentIds.removeAll(beforeSaveContentIds);

            assertThat(branchRepository.count()).isEqualTo(beforeBranchCount);
            assertThat(saveRepository.count()).isEqualTo(beforeSaveCount);
            assertThat(afterSaveContentIds).hasSize(1);

            String persistedSaveContentId = afterSaveContentIds.iterator().next();
            MongoDeleteOutbox outbox = mongoDeleteOutboxRepository.findAll().getFirst();

            assertThat(outbox.getTriggerType()).isEqualTo(TriggerType.COMPENSATE);
            assertThat(outbox.getDomainType()).isEqualTo(DomainType.BRANCH);
            assertThat(outbox.getOriginId()).isEqualTo(persistedSaveContentId);
            assertThat(loadOutboxSaveContentIds(outbox.getId())).containsExactly(persistedSaveContentId);
        }
    }

    @Nested
    @DisplayName("Mongo 저장 직후 실패")
    class MongoRollbackTest {

        @Test
        @DisplayName("SaveContent와 Branch/Save 모두 롤백되고 보상 Outbox도 생성되지 않는다")
        void createBranch_mongoFailure_rollsBackEverythingWithoutCompensateOutbox() {
            Fixture fixture = createSingleCommitFixture(unique("main"));
            long beforeBranchCount = branchRepository.count();
            long beforeSaveCount = saveRepository.count();
            Set<String> beforeSaveContentIds = currentSaveContentIds();

            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new RuntimeException("force mongo rollback after save");
            }).when(branchCreateMongoTxService).createSaveContentFromCommit(anyString());

            assertThatThrownBy(() -> branchCreateOrchestrator.create(new BranchCreateContext(
                    fixture.doc(),
                    fixture.branch(),
                    fixture.commit(),
                    unique("feature"),
                    fixture.commit().getCommitMongoId()
            ))).isInstanceOf(RuntimeException.class)
                    .hasMessage("force mongo rollback after save");

            assertThat(branchRepository.count()).isEqualTo(beforeBranchCount);
            assertThat(saveRepository.count()).isEqualTo(beforeSaveCount);
            assertThat(currentSaveContentIds()).isEqualTo(beforeSaveContentIds);
            assertThat(mongoDeleteOutboxRepository.findAll()).isEmpty();
        }
    }

    private Fixture createSingleCommitFixture(String branchName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = userRepository.saveAndFlush(User.builder()
                .email("branch-consistency-" + suffix + "@example.com")
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

        return new Fixture(user, doc, branch, commit);
    }

    private Set<String> currentSaveContentIds() {
        return saveContentRepository.findAll().stream()
                .map(SaveContent::getId)
                .collect(Collectors.toSet());
    }

    private List<String> loadOutboxSaveContentIds(Long outboxId) {
        return executeReadTransaction(() -> List.copyOf(mongoDeleteOutboxRepository.findById(outboxId)
                .orElseThrow()
                .getSaveContentIds()));
    }

    private <T> T executeReadTransaction(Supplier<T> supplier) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template.execute(status -> supplier.get());
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(User user, Doc doc, Branch branch, Commit commit) {
    }
}
