package io.ejangs.docsa.domain.branch.util;

import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;

public final class BranchMapper {

    public static Branch toEntity(BranchCreateRequest dto, Doc doc, Commit fromCommit) {
        return Branch.builder().name(dto.name()).document(doc).fromCommit(fromCommit).build();
    }

    public static BranchCreateResponse toBranchCreateResponse(Branch branch, Save save) {
        return new BranchCreateResponse(branch.getId(), save.getId());
    }
}
