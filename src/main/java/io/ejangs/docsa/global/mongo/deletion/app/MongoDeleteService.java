package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MongoDeleteService {

    private final SaveContentRepository saveContentRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;

    @Transactional(transactionManager = "mongoTransactionManager")
    public void deleteTarget(MongoIdsDto targetIds) {
        saveContentRepository.deleteAllById(targetIds.saveContentsIds());
        commitBlockSequenceRepository.deleteAllById(targetIds.commitBlockSequenceIds());
        blockRepository.deleteAllById(targetIds.blockIds());
    }
}
