package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchCreateMongoTxService {

    private final CommitContentAssembler commitContentAssembler;
    private final SaveContentRepository saveContentRepository;

    @Transactional(transactionManager = "mongoTransactionManager", rollbackFor = Exception.class)
    public String createSaveContentFromCommit(String commitMongoId, String saveContentId) {
        List<Map<String, Object>> blockContents = commitContentAssembler.assemble(commitMongoId);

        SaveContent saveContent = SaveContent.builder()
                .id(saveContentId)
                .content(blockContents)
                .build();

        return saveContentRepository.insert(saveContent).getId();
    }
}
