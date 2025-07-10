package io.ejangs.docsa.domain.block.util;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.block.entity.Block;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.global.util.JsonUtil;

public class BlockMapper {

    public static Block toEntity(Document document, BlockDto blockDto) {
        return Block.builder()
                .document(document)
                .uniqueId(blockDto.id())
                .type(blockDto.type())
                .data(JsonUtil.jsonToString(blockDto.data()))
                .build();
    }
}
