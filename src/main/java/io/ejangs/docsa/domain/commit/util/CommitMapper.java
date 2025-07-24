package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import java.util.List;
import java.util.Map;

public class CommitMapper {

    public static Commit toEntity(Branch branch, CreateCommitRequest commitRequest, String cbsId) {
        return Commit.builder()
                .title(commitRequest.title())
                .description(commitRequest.description())
                .commitMongoId(cbsId)
                .branch(branch)
                .build();
    }

    public static Commit toEntity(Branch branch, CreateCommitRequest commitRequest) {
        return Commit.builder()
                .title(commitRequest.title())
                .description(commitRequest.description())
                .branch(branch)
                .build();
    }

    public static Commit toEntity(Branch branch, MergeCommitRequest request) {
        return Commit.builder()
                .title(request.title())
                .description(request.description())
                .branch(branch)
                .build();
    }

    public static CreateCommitResponse toCreateCommitResponse(Commit commit) {
        return new CreateCommitResponse(commit.getId());
    }

    public static CommitResponse toCommitResponse(List<Map<String, Object>> content) {
        return new CommitResponse(content);
    }

    public static CompareMergeCommitResponse toCompareMergeCommitResponse(
            List<Map<String, Object>> base, List<Map<String, Object>> target) {
        return new CompareMergeCommitResponse(base, target);
    }
}
