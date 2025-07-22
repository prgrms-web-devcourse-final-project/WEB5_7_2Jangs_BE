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
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteRetryService;
import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteFailure;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private MongoDeleteRetryService retryService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
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

        doThrow(new RuntimeException("첫 번째 시도 실패"))
                .doCallRealMethod()
                .when(retryService).deleteMongoData(any());

        // when
        docService.delete(docId, user.getId());

        // then
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    verify(retryService, times(2)).deleteMongoData(any());

                    List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                    assertThat(failures).isEmpty();
                });
    }

    record MongoDeleteFailureDto(
            int saveSize,
            int commitSize,
            int blockSize,
            boolean resolved
    ) {

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
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            docService.delete(docId, user.getId());
        });

        // then
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    // 트랜잭션 안에서 Lazy 필드를 모두 접근해서 값으로 만들어둠
                    MongoDeleteFailureDto failureDto = transactionTemplate.execute(status -> {
                        List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                        assertThat(failures).hasSize(1);

                        MongoDeleteFailure failure = failures.get(0);

                        return new MongoDeleteFailureDto(
                                failure.getSaveContentIds().size(),
                                failure.getCommitBlockSequenceIds().size(),
                                failure.getBlockIds().size(),
                                failure.getResolved()
                        );
                    });

                    assertThat(failureDto.saveSize()).isGreaterThan(0);
                    assertThat(failureDto.commitSize()).isGreaterThan(0);
                    assertThat(failureDto.blockSize()).isGreaterThan(0);
                    assertThat(failureDto.resolved()).isFalse();
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

        // when
        docService.delete(docId, user.getId());

        // then
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    verify(retryService, times(1)).deleteMongoData(any());

                    List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAll();
                    assertThat(failures).isEmpty();
                });
    }

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