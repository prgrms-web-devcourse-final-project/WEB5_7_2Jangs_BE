package io.ejangs.docsa.domain.doc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.app.create.DocCreateMySqlTxService;
import io.ejangs.docsa.domain.doc.app.DocCommandService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DocCommandServiceIntegrationTests {

    @Autowired
    private DocCommandService docCommandService;

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
        DocCreateResponse response = docCommandService.create(request, user.getId());

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
        private DocCommandService docCommandService;
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

            assertThatThrownBy(() -> docCommandService.create(request, user.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(DatabaseErrorCode.DATABASE_ERROR.getMessage());
        }
    }

    @Nested
    @DisplayName("MySQL 실패시 MongoDB 보상 삭제")
    class MySqlFailureTest {

        @Autowired
        private DocCommandService docCommandService;

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
            assertThatThrownBy(() -> docCommandService.create(request, user.getId()))
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
        private DocCommandService docCommandService;

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
            assertThatThrownBy(() -> docCommandService.create(request, user.getId()))
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
        docCommandService.create(new DocTitleRequest(title), user.getId()); // 첫 번째 저장

        // when & then
        assertThatThrownBy(() -> docCommandService.create(new DocTitleRequest(title), user.getId()))
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
                docCommandService.create(request, nonexistentUserId)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

}
