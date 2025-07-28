package io.ejangs.docsa.domain.commit.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
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
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
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
    private MongoIdsCollector mongoIdsCollector;

    private User testUser;
    private Doc testDoc;
    private Branch baseBranch;
    private Branch targetBranch;
    private Commit commit10;
    private Commit commit20;
    private Commit commit21;
    private Commit commit22;
    private Commit commit30;
    private List<BlockDto> blocks;
    private List<String> blockOrders;

    @BeforeEach
    void setUp() throws JsonProcessingException {

        testUser = CommitIntegrationTestUtils.createTestUser();
        userRepository.save(testUser);

        TestDocIntegrationDto dto = CommitIntegrationTestUtils
                .createDocumentForIntegrationTest(testUser, cbsRepository, blockRepository);

        testDoc = dto.doc();
        baseBranch = dto.baseBranch();
        targetBranch = dto.targetBranch();
        commit10 = dto.commit10();
        commit20 = dto.commit20();
        commit21 = dto.commit21();
        commit22 = dto.commit22();
        commit30 = dto.commit30();

        docRepository.save(testDoc);

        TestCreateCommitRequestDto commitRequestDto = CommitIntegrationTestUtils
                .createCommitRequestDto();

        blocks = commitRequestDto.blocks();
        blockOrders = commitRequestDto.blockOrders();
    }

    @Test
    @DisplayName("정상적인 Commit 생성 테스트")
    void create_Commit_Success() throws Exception {

        CreateCommitRequest request = new CreateCommitRequest("title1",
                "description1",
                baseBranch.getId(),
                blocks,
                blockOrders);

        CreateCommitResponse response = commitService
                .createCommit(testDoc.getId(), request, testUser.getId());

        System.out.println("response = " + response);

        CommitResponse commit = commitService
                .getCommit(testDoc.getId(), response.id(), testUser.getId());

        System.out.println("commit = " + commit);
    }

    @Test
    @DisplayName("정상적인 최초 Commit 생성 테스트")
    void create_Init_Commit_Success() throws Exception {

        TestInitDocIntegrationDto dto = CommitIntegrationTestUtils
                .createInitDocumentForIntegrationTest(testUser, saveContentRepository);
        Branch main = dto.mainBranch();
        Doc doc = docRepository.save(dto.doc());

        CreateCommitRequest request = new CreateCommitRequest("title1",
                "description1",
                main.getId(),
                blocks,
                blockOrders);

        CreateCommitResponse response = commitService
                .createCommit(doc.getId(), request, testUser.getId());
    }
}
