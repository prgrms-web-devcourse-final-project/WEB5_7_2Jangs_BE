package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommitBlockSequenceFactory {

    private final BlockRepository blockRepository;

    public List<CommitBlockSequence> create(Commit commit, List<String> blockOrders,
            List<String> updatedBlockUniqueIds, Branch branch) {

        Map<String, Block> baseBlockMap = Optional.ofNullable(branch.getLeafCommit())
                .or(() -> Optional.ofNullable(branch.getFromCommit()))
                .map(commitSource -> commitSource.getCommitBlocks().stream()
                        .map(CommitBlockSequence::getCurrentBlock)
                        .collect(Collectors.toMap(Block::getUniqueId, Function.identity())))
                .orElse(Collections.emptyMap());

        List<Block> blocks = blockOrders.stream()
                .map(blockUniqueId -> {
                    if (updatedBlockUniqueIds.contains(blockUniqueId)) {
                        return blockRepository.findLatestByUniqueId(blockUniqueId)
                                .orElseThrow(
                                        () -> new CustomException(BlockErrorCode.BLOCK_NOT_FOUND));
                    } else {
                        return baseBlockMap.get(blockUniqueId);
                    }
                })
                .toList();

        return IntStream.range(0, blocks.size())
                .mapToObj(i -> CommitBlockSequence.builder()
                        .first(i == 0)
                        .commit(commit)
                        .currentBlock(blocks.get(i))
                        .nextBlock(i + 1 < blocks.size() ? blocks.get(i + 1) : null)
                        .build())
                .toList();
    }

}

