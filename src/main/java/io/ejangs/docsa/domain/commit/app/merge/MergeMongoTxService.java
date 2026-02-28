package io.ejangs.docsa.domain.commit.app.merge;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.global.mongo.deletion.dto.CommitMongoIdsDto;
import io.ejangs.docsa.domain.commit.util.CommitBlockSequenceMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MergeMongoTxService {

    private final BlockService blockService;
    private final CommitQueryService commitQueryService;

    @Transactional(transactionManager = "mongoTransactionManager")
    public CommitMongoIdsDto createMongoPart(List<BlockDto> blocks) {
        List<Block> savedBlocks = blockService.saveBlocks(blocks);

        List<String> blockSequence = savedBlocks.stream()
                .map(Block::getId)
                .toList();

        CommitBlockSequence cbs = CommitBlockSequenceMapper.toDocument(blockSequence);
        CommitBlockSequence savedCbs = commitQueryService.saveCommitBlockSequence(cbs);

        return new CommitMongoIdsDto(savedCbs.getId(), blockSequence);
    }
}
