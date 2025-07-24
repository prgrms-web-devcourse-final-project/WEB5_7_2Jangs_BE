package io.ejangs.docsa.mongoDeleteSystem;

import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MongoTransactionTestService {

    @Autowired
    private SaveContentRepository saveContentRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Transactional(transactionManager = "mongoTransactionManager")
    public void saveContent() {
        saveContentRepository.save(SaveContent.builder().build());

        if (true) {
            throw new RuntimeException("중간에 끊어버리기~");
        }

        saveContentRepository.save(SaveContent.builder().build());
    }

    @Transactional
    public void saveCommit() {
        commitRepository.save(Commit.builder().
                title("test-a").build());

        if (true) {
            throw new RuntimeException("중간에 끊어버리기~");
        }

        saveContentRepository.save(SaveContent.builder().build());
    }

}
