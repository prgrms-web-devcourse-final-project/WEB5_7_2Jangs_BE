package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;

public record BranchCreateContext(
        Doc doc,
        Branch fromBranch,
        Commit fromCommit,
        String branchName,
        String fromCommitMongoId,
        boolean leafCommit
) {

}
