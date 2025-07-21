package io.ejangs.docsa.global.mongoDeleteSystem.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.mongoDeleteSystem.dto.MongoIdsDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

public final class MongoDeleteMapper {

    public static MongoIdsDto toMongoIdsDto(Branch branch,
            List<String> commitBlockSequenceIds,
            List<String> blockIds) {
        List<String> saveContentsIds = Optional.ofNullable(branch.getSave())
                .map(Save::getSaveMongoId)
                .map(List::of)
                .orElse(List.of());

        return new MongoIdsDto(saveContentsIds, commitBlockSequenceIds, blockIds);
    }
}
