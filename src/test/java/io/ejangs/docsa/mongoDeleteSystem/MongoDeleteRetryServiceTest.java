package io.ejangs.docsa.mongoDeleteSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.mongoDeleteSystem.app.MongoDeleteRetryService;
import io.ejangs.docsa.global.mongoDeleteSystem.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongoDeleteSystem.entity.MongoDeleteFailure;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MongoDeleteRetryServiceTest {

    @Autowired
    private DocService docService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocRepository docRepository;

    @Autowired
    private SaveContentRepository saveContentRepository;

    @Autowired
    private CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private MongoDeleteFailureRepository mongoDeleteFailureRepository;

    @MockitoSpyBean
    private MongoDeleteRetryService retryService;

    @BeforeEach
    void setUp() {
        // 테스트 시작 전 MongoDeleteFailure 테이블 정리
        mongoDeleteFailureRepository.deleteAll();
    }

    @Test
    void delete_문서삭제_중_첫번째_시도는_실패하고_두번째_시도는_성공한다() throws Exception {
        // given
        User user = userRepository.saveAndFlush(DocTestUtils.createUser());
        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(
                user, saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAllAndFlush(docs);

        Long docId = docs.get(1).getId();

        // 첫 번째 호출에서만 예외 발생, 두 번째 호출부터는 정상 처리
        doThrow(new RuntimeException("첫 번째 시도 실패"))
                .doCallRealMethod()
                .when(retryService).deleteMongoData(any());

        // when
        docService.delete(docId, user.getId());

        // then - 재시도 후 성공했으므로 MongoDeleteFailure는 저장되지 않아야 함
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // 메서드가 2번 호출되었는지 확인 (첫 번째 실패, 두 번째 성공)
                    verify(retryService, times(2)).deleteMongoData(any());

                    List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                    assertThat(failures).isEmpty();
                });
    }

    @Test
    void delete_문서삭제_중_3회_모두_실패하면_MongoDeleteFailure가저장된다() throws Exception {
        // given
        User user = userRepository.saveAndFlush(DocTestUtils.createUser());
        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(
                user, saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAllAndFlush(docs);

        Long docId = docs.get(1).getId();

        // 모든 호출에서 예외 발생
        doThrow(new RuntimeException("모든 시도 실패"))
                .when(retryService).deleteMongoData(any());

        // when
        docService.delete(docId, user.getId());
        // then - 3회 모두 실패했으므로 MongoDeleteFailure가 저장되어야 함
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // 메서드가 3번 호출되었는지 확인
                    verify(retryService, times(3)).deleteMongoData(any());

                    List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                    assertThat(failures).hasSize(1);

                    MongoDeleteFailure failure = failures.get(0);
                    assertThat(failure.getSaveContentIds()).isNotEmpty();
                    assertThat(failure.getCommitBlockSequenceIds()).isNotEmpty();
                    assertThat(failure.getBlockIds()).isNotEmpty();
                    assertThat(failure.getResolved()).isFalse();
                });
    }

    @Test
    void delete_문서삭제_시_정상처리되면_MongoDeleteFailure가저장되지않는다() throws Exception {
        // given
        User user = userRepository.saveAndFlush(DocTestUtils.createUser());
        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(
                user, saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAllAndFlush(docs);

        Long docId = docs.get(1).getId();

        // when - 정상 처리 (mocking하지 않음)
        docService.delete(docId, user.getId());

        // then - 정상 처리되었으므로 MongoDeleteFailure는 저장되지 않아야 함
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    verify(retryService, times(1)).deleteMongoData(any());

                    List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                    assertThat(failures).isEmpty();
                });
    }

    // 디버깅을 위한 테스트 메서드 추가
    @Test
    void 이벤트_리스너가_정상적으로_동작하는지_확인() throws Exception {
        // given
        User user = userRepository.saveAndFlush(DocTestUtils.createUser());
        List<Doc> docs = DocTestUtils.createDocumentListForIntegrationTest(
                user, saveContentRepository, commitBlockSequenceRepository, blockRepository);
        docRepository.saveAllAndFlush(docs);

        Long docId = docs.get(1).getId();

        // when
        docService.delete(docId, user.getId());

        // then - 이벤트 리스너가 호출되었는지 확인
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // deleteMongoData가 적어도 1번은 호출되어야 함
                    verify(retryService, times(1)).deleteMongoData(any());
                });
    }
}