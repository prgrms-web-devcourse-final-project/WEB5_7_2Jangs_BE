package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchQueryService {

    private final BranchRepository branchRepository;

    public Branch getById(Long id) {
        return branchRepository.findById(id).orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));
    }

    public List<BranchGraphDto> getBranchGraphList(Long docId) {
        return branchRepository.findBranchGraphDtoList(docId);
    }

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

    public boolean existsSubBranchByFromCommitIds(List<Long> commitIds) {
        return branchRepository.existsByFromCommitIdIn(commitIds);
    }

    public void delete(Branch branch) {
        branchRepository.delete(branch);
    }

    //TODO: 검증하는 메소드는 별도의 ex.ValidationService로 분리(모든 도메인)
    public void checkBranchInDocOwnedByUser(Long documentId, Long branchId, Long userId) {
        boolean exists =
                branchRepository.existsByIdAndDocIdAndDocUserId(branchId, documentId, userId);
        if (!exists) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
    }

    public boolean checkFromOrRootCommitInBranch(Commit commit) {
        return branchRepository.existsByRootCommitIdOrFromCommitId(commit.getId());
    }

    public void checkDuplicatedWithBranchName(Long docId, String branchName) {
        boolean isDuplicate = branchRepository.existsByDocIdAndName(docId, branchName);

        if (isDuplicate) {
            throw new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED);
        }
    }
}
