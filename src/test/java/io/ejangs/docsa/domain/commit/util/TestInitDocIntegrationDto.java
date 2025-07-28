package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.entity.Doc;

public record TestInitDocIntegrationDto(
        Doc doc,
        Branch mainBranch
) {

}
