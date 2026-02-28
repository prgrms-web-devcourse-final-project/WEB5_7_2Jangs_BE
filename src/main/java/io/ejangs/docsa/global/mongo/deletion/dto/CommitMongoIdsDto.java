package io.ejangs.docsa.global.mongo.deletion.dto;

import java.util.List;

public record CommitMongoIdsDto(
        String cbsId,
        List<String> blockIds
) {

}
