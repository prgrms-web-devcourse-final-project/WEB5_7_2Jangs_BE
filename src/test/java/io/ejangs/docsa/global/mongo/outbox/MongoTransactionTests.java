package io.ejangs.docsa.global.mongo.outbox;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MongoTransactionTests {

    @Autowired
    MongoTransactionTestService mongoTransactionTestService;

    @Autowired
    SaveContentRepository saveContentRepository;

    @Autowired
    CommitRepository commitRepository;

    @Autowired
    BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        branchRepository.deleteAll();
        commitRepository.deleteAll();
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

    @Test
    void rdbTransactionShouldRollbackOnException() {
        long before = saveContentRepository.count();

        try {
            mongoTransactionTestService.saveCommit();
        } catch (Exception ignored) {
        }

        long after = saveContentRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
