package io.ejangs.docsa.domain.block.util;

import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import java.util.List;
import java.util.stream.Collectors;

public class BlockMapper {

    public static Block toDocument(BlockDto block) {
        return Block.builder()
                .content(block.data())
                .build();
    }

    public static List<Block> toDocument(List<BlockDto> blocks) {
        return blocks.stream()
                .map(BlockMapper::toDocument)
                .collect(Collectors.toList());
    }
}
