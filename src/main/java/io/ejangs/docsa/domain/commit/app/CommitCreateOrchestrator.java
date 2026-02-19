package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommitCreateOrchestrator {

    private final BlockService blockService;
    private final CommitBlockSequenceRepository cbsRepository;
    private final CommitMySqlTxService commitMySqlTxService;

}
