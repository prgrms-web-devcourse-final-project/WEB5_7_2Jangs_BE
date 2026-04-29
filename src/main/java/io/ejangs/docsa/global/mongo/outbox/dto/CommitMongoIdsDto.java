package io.ejangs.docsa.global.mongo.outbox.dto;

import java.util.List;

public record CommitMongoIdsDto(
        String cbsId,
        List<String> blockIds
) {

}
