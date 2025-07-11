package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;

public class CommitMapper {

    public static Commit toEntity(Branch branch, CreateCommitRequest commitRequest) {
        return Commit.builder()
                .title(commitRequest.title())
                .description(commitRequest.description())
                .branch(branch)
                .build();
    }

    public static CreateCommitResponse toCreateCommitResponse(Commit commit) {
        return new CreateCommitResponse(commit.getId());
    }
}
