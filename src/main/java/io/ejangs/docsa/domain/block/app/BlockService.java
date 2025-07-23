package io.ejangs.docsa.domain.block.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.block.util.BlockMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;

    public List<Block> saveBlocks(List<BlockDto> blockDtos) {
        List<Block> blocks = BlockMapper.toDocument(blockDtos);
        return blockRepository.saveAll(blocks);
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
