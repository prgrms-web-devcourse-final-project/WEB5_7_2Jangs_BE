package io.ejangs.docsa.global.mongoDeleteSystem.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.mongoDeleteSystem.dto.MongoIdsDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoIdsMapper {

    private final CommitBlockSequenceRepository commitBlockSequenceRepository;

    public MongoIdsDto toMongoIdsDto(List<Branch> branches) {
        List<String> saveContentMongoIds = branches.stream()
                .map(Branch::getSave)
                .filter(Objects::nonNull)
                .map(Save::getSaveMongoId)
                .toList();

        List<String> commitBlockSequenceIds = branches.stream()
                .flatMap(branch -> branch.getCommits().stream())
                .map(Commit::getCommitMongoId)
                .distinct()
                .toList();

        Set<String> blockIds = commitBlockSequenceIds.stream()
                .map(commitBlockSequenceRepository::findById)
                .flatMap(Optional::stream) // Optional이 비어있으면 skip
                .flatMap(cbs -> cbs.getBlockOrders().stream())
                .collect(Collectors.toSet());

        return new MongoIdsDto(saveContentMongoIds, commitBlockSequenceIds,
                new ArrayList<>(blockIds));
    }
}


