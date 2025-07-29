package io.ejangs.docsa.domain.commit.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitIntegrationTestUtils;
import io.ejangs.docsa.domain.commit.util.TestDocIntegrationDto;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DeleteCommitIntegrationTest {

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

    /*
    commit10
        |
        |
    commit20
        |     \
        |       commit21
        |           |
        |       commit22
        |     /
    commit30
     */

    @BeforeEach
    void setUp() throws JsonProcessingException {
        // given
        // 테스트 사용자 생성
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
    }

    @Test
    @DisplayName("기본 설정 확인 테스트")
    void init_Doc_Status() throws Exception {

        assertEquals(baseBranch.getRootCommit().getId(), commit10.getId());
        assertEquals(baseBranch.getLeafCommit().getId(), commit30.getId());

        assertEquals(targetBranch.getFromCommit().getId(), commit20.getId());
        assertEquals(targetBranch.getRootCommit().getId(), commit21.getId());
        assertEquals(targetBranch.getLeafCommit().getId(), commit22.getId());

        assertEquals(2, edgeRepository.findAllByCommitIdInPrevOrNext(commit30.getId()).size());
        assertEquals(5, edgeRepository.findAll().size());
    }

    @Test
    @DisplayName("정상적인 삭제 테스트")
    void delete_Commit_Success() {
        // given

        // when
        commitService.deleteCommit(testDoc.getId(), commit22.getId(), testUser.getId());

        // then
        assertEquals(baseBranch.getLeafCommit().getId(), commit30.getId());
        assertEquals(targetBranch.getLeafCommit().getId(), commit21.getId());
        assertEquals(3, edgeRepository.count());
        assertEquals(4, commitRepository.count());
        assertEquals(2, branchRepository.count());
    }

    @Test
    @DisplayName("Merge된 기록 삭제 테스트")
    void delete_Merge_Commit_Success() {
        // given

        // when
        commitService.deleteCommit(testDoc.getId(), commit30.getId(), testUser.getId());

        // then
        assertEquals(baseBranch.getLeafCommit().getId(), commit20.getId());
        assertEquals(3, edgeRepository.count());
        assertEquals(4, commitRepository.count());
        assertEquals(2, branchRepository.count());
    }

    @Test
    @DisplayName("문서의 작성자와 일치하지 않는 유저의 삭제 시도 시 예외 발생")
    void invalid_User_Fail() {
        // given
        User invalidUser = CommitIntegrationTestUtils.createInvalidTestUser();
        userRepository.save(invalidUser);

        // when & then
        assertThatThrownBy(
                () -> commitService.deleteCommit(testDoc.getId(), commit30.getId(), invalidUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(DocErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 기록의 Id로 삭제 시도 시 예외 발생")
    void deleteCommit_Commit_NotFound() {
        // given
        Long commitId = 999L;

        // when & then
        assertThatThrownBy(
                () -> commitService.deleteCommit(testDoc.getId(), commitId, testUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.COMMIT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 문서의 Id로 삭제 시도 시 예외 발생")
    void deleteCommit_Doc_NotFound() {
        // given
        Long docId = 999L;

        // when & then
        assertThatThrownBy(
                () -> commitService.deleteCommit(docId, commit30.getId(), testUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(DocErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("LeafCommit이 아닌 기록 삭제 시도 시 예외 발생")
    void deleteCommit_Is_Not_LeafCommit_Can_Not_Delete() {
        // given

        // when & then
        assertThatThrownBy(
                () -> commitService.deleteCommit(testDoc.getId(), commit20.getId(), testUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.IS_NOT_LEAF_COMMIT.getMessage());
    }

    @Test
    @DisplayName("RootCommit인 기록 삭제 시도 시 예외 발생")
    void deleteCommit_Is_RootCommit_Can_Not_Delete() {
        // given

        // when & then
        assertThatThrownBy(
                () -> commitService.deleteCommit(testDoc.getId(), commit21.getId(), testUser.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CommitErrorCode.IS_NOT_LEAF_COMMIT.getMessage());
    }

    @Test
    @DisplayName("블록 컬렉터 테스트1")
    void collect_MongoIdsDto_Test1() throws Exception {
        // given
        List<Commit> prevCommits = List.of(commit20, commit22);

        // when
        MongoIdsDto mongoIdsDto = mongoIdsCollector.collectFrom(prevCommits, commit30);

        // then
        for (String commitBlockSequenceId : mongoIdsDto.commitBlockSequenceIds()) {
            System.out.println("commitBlockSequenceId = " + commitBlockSequenceId);
        }

        for (String blockId : mongoIdsDto.blockIds()) {
            System.out.println("blockId = " + blockId);
            Block block = blockRepository.findById(blockId).orElse(null);
            System.out.println("findById(blockId) = " + block.getContent().values());
        }
    }

    @Test
    @DisplayName("블록 컬렉터 테스트2")
    void collect_MongoIdsDto_Test2() throws Exception {
        // given
        List<Commit> prevCommits = List.of(commit21);

        // when
        MongoIdsDto mongoIdsDto = mongoIdsCollector.collectFrom(prevCommits, commit22);

        // then
        for (String commitBlockSequenceId : mongoIdsDto.commitBlockSequenceIds()) {
            System.out.println("commitBlockSequenceId = " + commitBlockSequenceId);
        }

        for (String blockId : mongoIdsDto.blockIds()) {
            System.out.println("blockId = " + blockId);
            Block block = blockRepository.findById(blockId).orElse(null);
            System.out.println("findById(blockId) = " + block.getContent().values());
        }
    }
}
