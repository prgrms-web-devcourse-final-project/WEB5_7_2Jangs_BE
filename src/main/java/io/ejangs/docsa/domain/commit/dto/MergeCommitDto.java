package io.ejangs.docsa.domain.commit.dto;

import io.ejangs.docsa.domain.commit.entity.Commit;

public record MergeCommitDto(
        Commit commit,
        String saveMongoIds
) {

}
