package io.ejangs.docsa.domain.doc.dto;

import java.util.List;

public record DocDeleteMongoIdsDto(
        List<String> saveContentsIds,
        List<String> commitBlockSequenceIds
) {

}
