package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchWriter {

    private final BranchRepository branchRepository;

    public Branch save(Branch branch) {
        return branchRepository.save(branch);
    }

    public Branch createBranch(Doc doc, String branchName) {
        Branch branch = branchRepository.save(Branch.builder().name(branchName).doc(doc).build());
        RenewUpdatedAtHelper.touch(branch);
        return branch;
    }

    public Branch createBranch(Doc doc, String branchName, Commit fromCommit) {
        Branch branch = Branch.builder().name(branchName).doc(doc).fromCommit(fromCommit).build();
        return branchRepository.save(branch);
    }

    public void delete(Branch branch) {
        branchRepository.delete(branch);
    }
}
