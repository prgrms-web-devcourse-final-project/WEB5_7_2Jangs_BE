package io.ejangs.docsa.domain.block.util;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.util.JsonUtil;

public class BlockMapper {

    public static Block toEntity(Doc doc, BlockDto blockDto) {
        return Block.builder()
                .document(doc)
                .uniqueId(blockDto.id())
                .type(blockDto.type())
                .data(JsonUtil.jsonToString(blockDto.data()))
                .tunes(JsonUtil.jsonToString(blockDto.tunes()))
                .build();
    }
}
