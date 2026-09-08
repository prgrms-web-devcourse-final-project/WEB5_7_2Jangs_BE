package io.ejangs.docsa.domain.block.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.util.BlockMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;

    public List<Block> saveBlocks(List<Map<String, Object>> blockDataList) {
        List<Block> blocks = BlockMapper.toDocument(blockDataList);
        return blockRepository.saveAll(blocks);
    }

    public List<Block> insertBlocks(List<Map<String, Object>> blockDataList, List<String> blockIds) {
        if (blockDataList.size() != blockIds.size()) {
            throw new IllegalArgumentException("block data and planned ids must have the same size");
        }
        List<Block> blocks = java.util.stream.IntStream.range(0, blockDataList.size())
                .mapToObj(index -> Block.builder()
                        .id(blockIds.get(index))
                        .content(blockDataList.get(index))
                        .build())
                .toList();
        return blockRepository.insert(blocks);
    }

    public void deleteAll(List<Block> savedBlock) {
        blockRepository.deleteAll(savedBlock);
    }

    public List<Block> getAllById(List<String> blockIds) {
        return blockRepository.findAllById(blockIds);
    }

    public void deleteBlock(String blockId) {
        blockRepository.deleteById(blockId);
    }
}
