package io.ejangs.docsa.domain.block.util;

import io.ejangs.docsa.domain.block.document.Block;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BlockMapper {

    public static Block toDocument(Map<String, Object> block) {
        return Block.builder()
                .content(block)
                .build();
    }

    public static List<Block> toDocument(List<Map<String, Object>> blocks) {
        return blocks.stream()
                .map(BlockMapper::toDocument)
                .collect(Collectors.toList());
    }
}
