package io.ejangs.docsa.domain.branch.util;

import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;

public final class BranchMapper {

    public static Branch toEntity(BranchCreateRequest dto, Doc doc, Commit fromCommit) {
        return Branch.builder().name(dto.name()).doc(doc).fromCommit(fromCommit).build();
    }

    public static BranchCreateResponse toBranchCreateResponse(Branch branch, Save save) {
        return new BranchCreateResponse(branch.getId(), save.getId());
    }

    public static BranchRenameResponse toBranchRenameResponse(Branch branch) {
        return new BranchRenameResponse(branch.getId(), branch.getName());
    }
}
