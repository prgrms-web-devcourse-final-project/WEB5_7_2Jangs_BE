package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.save.app.SaveWriter;
import io.ejangs.docsa.domain.save.document.SaveContent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MergeMongoTxService {

    private final SaveWriter saveWriter;

    @Transactional(transactionManager = "mongoTransactionManager", rollbackFor = Exception.class)
    public String createMongoPart(List<Map<String, Object>> content) {
        SaveContent saveContent = SaveContent.builder()
                .content(content)
                .build();

        SaveContent saved = saveWriter.saveSaveContent(saveContent);
        return saved.getId();
    }
}
