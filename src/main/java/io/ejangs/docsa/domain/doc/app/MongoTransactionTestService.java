package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MongoTransactionTestService {

    private final SaveContentRepository saveContentRepository;

    @Transactional
    public void saveContent() {
        saveContentRepository.save(SaveContent.builder().build());

        if (true) {
            throw new RuntimeException("중간에 끊어버리기~");
        }

        saveContentRepository.save(SaveContent.builder().build());
    }

}
