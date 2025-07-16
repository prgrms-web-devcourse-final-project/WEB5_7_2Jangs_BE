package io.ejangs.docsa.domain.doc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto.RecentType;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@Transactional
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

    @Value("${default.branch}")
    private String defaultBranchName;

    @AfterEach
    void cleanup() {
        saveContentRepository.deleteAll();
    }

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
        @DisplayName("Mongo 저장 실패 시 문서 생성 트랜잭션이 중단된다")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
            // findAll이 같은 트랜잭션 안에서 수행 되면 rollback 되기 전 상태를 그대로 읽을 수 있음
        void MongoFailRdbTransaction() {
            User user = userRepository.save(DocTestUtils.createUser());
            DocTitleRequest request = new DocTitleRequest("Mongo 실패 케이스");

            when(saveContentRepository.save(any()))
                    .thenThrow(new MongoTimeoutException("Mongo 연결 실패"));

            assertThatThrownBy(() -> docService.create(request, user.getId()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(DocErrorCode.FAIL_CREATE_DOCUMENT.getMessage());

            assertThat(docRepository.findAll()).isEmpty();
            assertThat(branchRepository.findAll()).isEmpty();
            assertThat(saveRepository.findAll()).isEmpty();
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
    @DisplayName("사이드바 문서리스트 조회 - 최근 활동이 커밋 또는 저장 중 최신으로 설정됨")
    void getSimpleDocumentList() throws Exception {
        // given
        User user = userRepository.save(DocTestUtils.createUser());

        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(user,
                saveContentRepository);
        docRepository.saveAll(docs);

        // when
        List<DocListSimpleResponse> results = docService.getSimpleList(user.getId());

        // then
        assertEquals(2, results.size());

        DocListSimpleResponse first = results.get(0);  // 최신 updatedAt 기준으로 정렬되었다고 가정
        DocListSimpleResponse second = results.get(1);

        // 저장이 없음 -> 최신 커밋
        assertEquals("문서 1", first.title());
        assertEquals(RecentType.COMMIT, first.recent().recentType());
        assertEquals(2L, first.recent().recentTypeId());

        // 저장이 있음
        assertEquals("문서 2", second.title());
        assertEquals(RecentType.SAVE, second.recent().recentType());
        assertEquals(2L, second.recent().recentTypeId());
    }
}