package io.ejangs.docsa.domain.commit.dto;

import java.util.List;

public record CommitMongoIdsDto(
        String cbsId,
        List<String> blockIds
) {

}
