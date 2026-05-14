package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitMySqlTxService;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitIntegrationTestUtils;
import io.ejangs.docsa.domain.commit.util.TestCreateCommitRequestDto;
import io.ejangs.docsa.domain.commit.util.TestDocIntegrationDto;
import io.ejangs.docsa.domain.commit.util.TestInitDocIntegrationDto;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.edge.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CreateCommitIntegrationTest {

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

    @Autowired
    private SaveContentRepository saveContentRepository;

    @Autowired
    private CommitBlockSequenceRepository cbsRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    private User testUser;
    private Doc testDoc;
    private Branch baseBranch;
    private Branch targetBranch;
    private Commit commit10;
    private Commit commit20;
    private Commit commit21;
    private Commit commit22;
    private Commit commit30;
    private List<Map<String, Object>> blocks;
    private List<String> blockOrders;

    @BeforeEach
    void setUp() throws JsonProcessingException {

        testUser = CommitIntegrationTestUtils.createTestUser();
        userRepository.save(testUser);

        TestDocIntegrationDto dto =
                CommitIntegrationTestUtils.createDocumentForIntegrationTest(testUser, cbsRepository,
                        blockRepository);

        testDoc = dto.doc();
        baseBranch = dto.baseBranch();
        targetBranch = dto.targetBranch();
        commit10 = dto.commit10();
        commit20 = dto.commit20();
        commit21 = dto.commit21();
        commit22 = dto.commit22();
        commit30 = dto.commit30();

        docRepository.save(testDoc);

        TestCreateCommitRequestDto commitRequestDto =
                CommitIntegrationTestUtils.createCommitRequestDto();

        blocks = commitRequestDto.blocks();
        blockOrders = commitRequestDto.blockOrders();
    }

    @Test
    @DisplayName("정상적인 Commit 생성 테스트")
    void create_Commit_Success() throws Exception {
        long beforeCommitCount = commitRepository.count();
        long beforeCbsCount = cbsRepository.count();
        long beforeBlockCount = blockRepository.count();

        CreateCommitRequest request =
                new CreateCommitRequest("title1", "description1", baseBranch.getId(), blocks,
                        blockOrders);

        CreateCommitResponse response =
                commitService.createCommit(testDoc.getId(), request, testUser.getId());

        CommitResponse commit =
                commitService.getCommit(testDoc.getId(), response.id(), testUser.getId());

        assertThat(response.id()).isNotNull();
        assertThat(commit.content()).isNotEmpty();
        assertThat(commitRepository.count()).isEqualTo(beforeCommitCount + 1);
        assertThat(cbsRepository.count()).isEqualTo(beforeCbsCount + 1);
        assertThat(blockRepository.count()).isEqualTo(beforeBlockCount + blocks.size());
    }

    @Test
    @DisplayName("정상적인 최초 Commit 생성 테스트")
    void create_Init_Commit_Success() throws Exception {

        TestInitDocIntegrationDto dto =
                CommitIntegrationTestUtils.createInitDocumentForIntegrationTest(testUser,
                        saveContentRepository);
        Branch main = dto.mainBranch();
        Doc doc = docRepository.save(dto.doc());

        CreateCommitRequest request =
                new CreateCommitRequest("title1", "description1", main.getId(), blocks,
                        blockOrders);

        CreateCommitResponse response =
                commitService.createCommit(doc.getId(), request, testUser.getId());

        Commit savedCommit = commitRepository.findById(response.id()).orElseThrow();
        assertThat(savedCommit.getBranch().getRootCommit()).isNotNull();
        assertThat(savedCommit.getBranch().getLeafCommit()).isNotNull();
    }

    @Test
    @DisplayName("동일 요청 2회 재시도 시 커밋은 중복 생성된다(멱등키 미적용 상태)")
    void create_Commit_DuplicateRequest_CreatesTwoCommits() {
        long beforeCommitCount = commitRepository.count();

        CreateCommitRequest request =
                new CreateCommitRequest("duplicate title", "description", baseBranch.getId(), blocks,
                        blockOrders);

        CreateCommitResponse first = commitService.createCommit(testDoc.getId(), request, testUser.getId());
        CreateCommitResponse second = commitService.createCommit(testDoc.getId(), request, testUser.getId());

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(commitRepository.count()).isEqualTo(beforeCommitCount + 2);
    }

    @Test
    @DisplayName("잘못된 blockOrders 요청 시 커밋 생성 실패 및 RDB 커밋 미생성")
    void create_Commit_InvalidBlockOrder_Fail() {
        long beforeCommitCount = commitRepository.count();

        CreateCommitRequest request =
                new CreateCommitRequest(
                        "invalid order",
                        "description",
                        baseBranch.getId(),
                        blocks,
                        List.of("aa1", "aa2", "aa3", "aa4", "not-exist-editor-id")
                );

        assertThatThrownBy(() -> commitService.createCommit(testDoc.getId(), request, testUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(BlockSequenceErrorCode.BLOCK_SEQUENCE_INVALID.getMessage());

        assertThat(commitRepository.count()).isEqualTo(beforeCommitCount);
    }

    @Nested
    @DisplayName("MySQL 실패 + Mongo 보상")
    @Transactional
    class MySqlFailureCompensationTest {

        @MockitoBean
        private CommitMySqlTxService commitMySqlTxService;

        @Test
        @DisplayName("Mongo 성공 후 MySQL 실패 시 보상 Outbox가 생성된다")
        void mysqlFail_compensateMongoDelete() {
            long beforeCbsCount = cbsRepository.count();
            long beforeBlockCount = blockRepository.count();
            long beforeOutboxCount = mongoDeleteOutboxRepository.count();

            CreateCommitRequest request =
                    new CreateCommitRequest("mysql fail", "description", baseBranch.getId(), blocks, blockOrders);

            when(commitMySqlTxService.createMySqlPart(any(), any(), any(), anyString()))
                    .thenThrow(new RuntimeException("mysql fail"));

            assertThatThrownBy(() -> commitService.createCommit(testDoc.getId(), request, testUser.getId()))
                    .isInstanceOf(CustomException.class)
                            .hasMessage(CommitErrorCode.FAIL_CREATE_COMMIT.getMessage());

            assertThat(cbsRepository.count()).isEqualTo(beforeCbsCount + 1);
            assertThat(blockRepository.count()).isEqualTo(beforeBlockCount + blocks.size());
            assertThat(mongoDeleteOutboxRepository.count()).isEqualTo(beforeOutboxCount + 1);

            assertThat(mongoDeleteOutboxRepository.findAll())
                    .anySatisfy(outbox -> {
                        assertThat(outbox.getTriggerType()).isEqualTo(MongoDeleteOutbox.TriggerType.COMPENSATE);
                        assertThat(outbox.getDomainType()).isEqualTo(MongoDeleteOutbox.DomainType.COMMIT);
                        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.OPEN);
                        assertThat(outbox.getOriginId()).isNotBlank();
                    });
        }
    }
}
