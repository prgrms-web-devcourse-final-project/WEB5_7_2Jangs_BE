package io.ejangs.docsa.domain.doc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.app.create.DocCreateMySqlTxService;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.PageableFactory;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DocServiceIntegrationTests {

    @Autowired
    private DocService docService;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

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

    @Value("${default.branch}")
    private String defaultBranchName;

    @Test
    @DisplayName("문서 생성 시 문서, 브랜치, 세이브, 세이브컨텐츠가 모두 정상 저장된다")
    void documentCreateSuccess() throws Exception {
        // given: 유저 생성 및 저장
        User user = userRepository.save(DocTestUtils.createUser());

        String title = "첫 문서 신난다!";
        DocTitleRequest request = new DocTitleRequest(title);

        // when: 문서 생성 요청
        DocCreateResponse response = docService.create(request, user.getId());

        // then: 문서 저장 검증
        Doc savedDoc = docRepository.findById(response.id())
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));

        assertThat(savedDoc.getTitle()).isEqualTo(title);
        assertThat(savedDoc.getUser().getId()).isEqualTo(user.getId());
        assertThat(response.id()).isEqualTo(savedDoc.getId());

        // then: 브랜치 저장 검증
        List<Branch> branches = branchRepository.findAll();
        assertThat(branches).hasSize(1);
        Branch branch = branches.getFirst();
        assertThat(branch.getName()).isEqualTo(defaultBranchName);
        assertThat(branch.getDoc().getId()).isEqualTo(savedDoc.getId());

        // then: Save(RDB) 저장 검증
        List<Save> saves = saveRepository.findAll();
        assertThat(saves).hasSize(1);
        Save save = saves.getFirst();
        assertThat(save.getBranch().getId()).isEqualTo(branch.getId());

        // then: SaveContent(MongoDB) 저장 검증
        String mongoId = save.getSaveMongoId();
        Optional<SaveContent> content = saveContentRepository.findById(mongoId);
        assertThat(content).isPresent();
        assertThat(content.get().getContent()).isEmpty(); // 빈 JSON 확인
    }

    @Nested
    @DisplayName("Mongo 실패 케이스")
    class MongoFailureTest {

        @Autowired
        private DocService docService;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private DocRepository docRepository;
        @Autowired
        private BranchRepository branchRepository;
        @Autowired
        private SaveRepository saveRepository;

        @MockitoBean
        private SaveContentRepository saveContentRepository;

        @Test
        @DisplayName("Mongo 저장 실패 시 예외")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
            // findAll이 같은 트랜잭션 안에서 수행 되면 rollback 되기 전 상태를 그대로 읽을 수 있음
        void MongoFailRdbTransaction() {
            User user = userRepository.save(DocTestUtils.createUser());
            DocTitleRequest request = new DocTitleRequest("Mongo 실패 케이스");

            when(saveContentRepository.save(any()))
                    .thenThrow(new MongoTimeoutException("Mongo 연결 실패"));

            assertThatThrownBy(() -> docService.create(request, user.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(DatabaseErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    @Nested
    @DisplayName("MySQL 실패시 MongoDB 보상 삭제")
    class MySqlFailureTest {

        @Autowired
        private DocService docService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private SaveContentRepository saveContentRepository;

        @Autowired
        private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

        @MockitoBean
        private DocCreateMySqlTxService docCreateMySqlTxService; // MySQL 파트만 실패 유도

        @Test
        @DisplayName("MySQL 생성 실패 시 Mongo 보상 Outbox가 적재된다")
        void mysqlFail_createCompensateOutbox() {
            // given
            User user = userRepository.save(DocTestUtils.createUser());
            DocTitleRequest request = new DocTitleRequest("MySQL 실패 케이스");
            long beforeSaveContentCount = saveContentRepository.count();

            // Mongo는 정상 저장되고, MySQL 파트에서 예외가 터진 상황
            when(docCreateMySqlTxService.createMySqlPart(any(), any(), anyString()))
                    .thenThrow(new RuntimeException("MySQL 생성 실패"));

            // when & then
            assertThatThrownBy(() -> docService.create(request, user.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessage(DocErrorCode.FAIL_CREATE_DOCUMENT.getMessage());

            // Mongo에 저장된 데이터는 비동기 워커가 삭제하므로 즉시 1건 증가 상태다.
            assertThat(saveContentRepository.count()).isEqualTo(beforeSaveContentCount + 1);

            List<MongoDeleteOutbox> outboxes = mongoDeleteOutboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            MongoDeleteOutbox outbox = outboxes.getFirst();
            assertThat(outbox.getTriggerType()).isEqualTo(MongoDeleteOutbox.TriggerType.COMPENSATE);
            assertThat(outbox.getDomainType()).isEqualTo(MongoDeleteOutbox.DomainType.DOC);
            assertThat(outbox.getOriginId()).isNotBlank();
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.OPEN);
        }
    }
    @Nested
    @DisplayName("MySQL 실패 시 보상 Outbox 상태")
    class MySqlFailureWithCompensateOutboxTest {

        @Autowired
        private DocService docService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

        @MockitoBean
        private DocCreateMySqlTxService docCreateMySqlTxService;

        @Test
        @DisplayName("MySQL 생성 실패 시 보상 Outbox는 OPEN 상태로 저장된다")
        void mysqlFail_storeOpenOutbox() {

            // given
            User user = userRepository.save(DocTestUtils.createUser());
            DocTitleRequest request = new DocTitleRequest("보상 실패 케이스");

            // MySQL 파트 실패
            when(docCreateMySqlTxService.createMySqlPart(any(), any(), anyString()))
                    .thenThrow(new RuntimeException("MySQL 생성 실패"));

            // when & then
            assertThatThrownBy(() -> docService.create(request, user.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessage(DocErrorCode.FAIL_CREATE_DOCUMENT.getMessage());

            List<MongoDeleteOutbox> outboxes = mongoDeleteOutboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            assertThat(outboxes.getFirst().getStatus()).isEqualTo(OutboxStatus.OPEN);
            assertThat(outboxes.getFirst().getRetryCount()).isEqualTo(0);
        }
    }


    @Test
    @DisplayName("중복된 제목으로 문서를 생성할 경우 예외가 발생한다")
    void documentCreateFailByDuplicateTitle() {
        // given
        User user = userRepository.save(DocTestUtils.createUser());
        String title = "중복 제목 테스트";
        docService.create(new DocTitleRequest(title), user.getId()); // 첫 번째 저장

        // when & then
        assertThatThrownBy(() -> docService.create(new DocTitleRequest(title), user.getId()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 사용중인 제목입니다.");
    }

    @Test
    @DisplayName("문서 생성 실패 - 존재하지 않는 사용자 ID")
    void documentCreateFailTestNotFoundUser() {
        // given
        Long nonexistentUserId = 9999L; // 실제 DB에 없는 ID
        DocTitleRequest request = new DocTitleRequest("없는 유저 문서");

        // when & then
        CustomException ex = assertThrows(CustomException.class, () ->
                docService.create(request, nonexistentUserId)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("사이드바 문서리스트 조회 - 최근 저장 id가 설정됨")
    void getSimpleDocumentList() throws Exception {
        // given
        User user = userRepository.save(DocTestUtils.createUser());

        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(user,
                saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAll(docs);

        Pageable pageable = PageableFactory.create("updatedAt", "asc", 0, 10);

        // when
        Page<DocSimplePageResponse> page = docService.getSimplePage(user.getId(), pageable);
        List<DocSimplePageResponse> results = page.getContent();

        // then
        assertEquals(2, results.size());

        DocSimplePageResponse first = results.get(0);  // 최신 updatedAt 기준으로 정렬되었다고 가정
        DocSimplePageResponse second = results.get(1);

        assertEquals("문서 1", first.title());
        assertThat(first.recentSaveId()).isNotNull();

        assertEquals("문서 2", second.title());
        assertThat(second.recentSaveId()).isNotNull();
    }

    @Test
    @DisplayName("문서 리스트 조회 - 최신 활동 기준 정렬 및 미리보기 제공")
    void getDocListWithPreview() throws Exception {
        // given
        User user = userRepository.save(DocTestUtils.createUser());

        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(user,
                saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAll(docs);

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);

        // when
        Page<DocPageResponse> results = docService.getPage(user.getId(), pageable);

        // then
        assertEquals(2, results.getContent().size());

        DocPageResponse first = results.getContent().getFirst();  // updatedAt 기준 최신
        DocPageResponse second = results.getContent().get(1);

        assertEquals("문서 1", second.title());
        assertEquals(ThumbnailStatus.EMPTY, second.thumbnailStatus());
        assertThat(second.recentSaveId()).isNotNull();

        assertEquals("문서 2", first.title());
        assertEquals(ThumbnailStatus.EMPTY, first.thumbnailStatus());
        assertThat(first.recentSaveId()).isNotNull();
    }

    @Test
    @DisplayName("문서 리스트 조회 - 최신 브랜치의 저장 id를 응답한다")
    void getDocListReturnsLatestBranchSaveId() {
        // given
        User user = userRepository.save(DocTestUtils.createUser());
        Doc doc = Doc.builder()
                .title("최신 저장 id 테스트")
                .user(user)
                .build();
        Branch firstBranch = Branch.builder()
                .name(defaultBranchName)
                .doc(doc)
                .build();
        Save.builder()
                .branch(firstBranch)
                .saveMongoId("save-content-1")
                .build();

        Branch secondBranch = Branch.builder()
                .name("feature")
                .doc(doc)
                .build();
        Save latestSave = Save.builder()
                .branch(secondBranch)
                .saveMongoId("save-content-2")
                .build();

        docRepository.saveAndFlush(doc);

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);

        // when
        Page<DocPageResponse> result = docService.getPage(user.getId(), pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().recentSaveId()).isEqualTo(latestSave.getId());
    }

    @Test
    @DisplayName("문서 리스트 조회 - 페이지네이션 테스트 100개의 문서를 만들고 페이지 0에서는 10개만 조회한다.")
    void getDocListInPage() throws Exception {
        //given
        User user = userRepository.save(DocTestUtils.createUser());
        List<Doc> docs = DocTestUtils.createDocList(100, user);
        docRepository.saveAll(docs);

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);

        //when
        Page<DocPageResponse> result = docService.getPage(user.getId(), pageable);

        //then
        assertEquals(10, result.getContent().size());
        DocPageResponse first = result.getContent().getFirst();
        assertEquals("문서 keyword포함100", first.title());
        assertEquals(100L, first.id());

        DocPageResponse last = result.getContent().getLast();
        assertEquals("테스트 문서 91", last.title());
        assertEquals(91L, last.id());
    }

    @Test
    @DisplayName("문서 리스트 검색 - 300개의 전체 문서중 150개의 키워드포함 문서를 검색하여 페이지로 응답한다.")
    void searchListSuccess() {
        // given
        User user = userRepository.save(DocTestUtils.createUser());

        List<Doc> docs = DocTestUtils.createDocList(300, user);
        docRepository.saveAll(docs);

        String keyword = "keyword";
        Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());

        // when
        Page<DocPageResponse> result = docService.searchList(user.getId(), keyword, pageable);

        // then
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent())
                .extracting(DocPageResponse::title)
                .allMatch(title -> title.contains("keyword"));
        assertThat(result.getContent().getFirst().id()).isEqualTo(300);
    }
}
