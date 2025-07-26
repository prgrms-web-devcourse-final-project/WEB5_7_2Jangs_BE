package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;

public record TestDocIntegrationDto(
        Doc doc,
        Branch baseBranch,
        Branch targetBranch,
        Commit commit10,
        Commit commit20,
        Commit commit21,
        Commit commit22,
        Commit commit30
) {

}
