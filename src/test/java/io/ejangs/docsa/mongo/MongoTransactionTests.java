package io.ejangs.docsa.mongo;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.ejangs.docsa.domain.doc.app.MongoTransactionTestService;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MongoTxTestServiceTest {

    @Autowired
    MongoTransactionTestService mongoTransactionTestService;

    @Autowired
    SaveContentRepository saveContentRepository;

    @BeforeEach
    void setUp() {
        saveContentRepository.deleteAll();
    }

    @Test
    void mongoTransactionShouldRollbackOnException() {
        // given
        long before = saveContentRepository.count();

        // when
        try {
            mongoTransactionTestService.saveContent();
        } catch (Exception ignored) {
        }

        // then
        long after = saveContentRepository.count();

        assertThat(after).isEqualTo(before);
    }
}
