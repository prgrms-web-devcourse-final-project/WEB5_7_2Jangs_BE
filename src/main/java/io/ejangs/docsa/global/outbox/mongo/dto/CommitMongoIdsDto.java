package io.ejangs.docsa.global.outbox.mongo.dto;

import java.util.List;

public record CommitMongoIdsDto(
        String cbsId,
        List<String> blockIds
) {

}
