package io.ejangs.docsa.global.mongo.deletion.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MongoDeleteMapper {

    public static MongoIdsDto toMongoIdsDto(Branch branch,
            List<String> commitBlockSequenceIds,
            List<String> blockIds) {
        List<String> saveContentsIds = Optional.ofNullable(branch.getSave())
                .map(Save::getSaveMongoId)
                .filter(Objects::nonNull)
                .map(List::of)
                .orElse(List.of());

        return new MongoIdsDto(saveContentsIds, commitBlockSequenceIds, blockIds);
    }
}
