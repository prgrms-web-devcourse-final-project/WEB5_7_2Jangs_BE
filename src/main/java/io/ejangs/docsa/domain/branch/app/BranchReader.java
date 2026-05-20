package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchReader {

    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public Branch getById(Long id) {
        return branchRepository.findById(id).orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<BranchGraphDto> getBranchGraphList(Long docId) {
        return branchRepository.findBranchGraphDtoList(docId);
    }

    @Transactional(readOnly = true)
    public boolean existsSubBranchByFromCommitIds(List<Long> commitIds) {
        return branchRepository.existsByFromCommitIdIn(commitIds);
    }

    public void checkBranchInDocOwnedByUser(Long documentId, Long branchId, Long userId) {
        boolean exists =
                branchRepository.existsByIdAndDocIdAndDocUserId(branchId, documentId, userId);
        if (!exists) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
    }

    public boolean checkFromCommitOrMergeCommitInBranch(Commit commit) {
        return branchRepository.existsByFromOrMergeTargetCommitId(commit.getId());
    }

    public void checkDuplicatedWithBranchName(Long docId, String branchName) {
        boolean isDuplicate = branchRepository.existsByDocIdAndName(docId, branchName);

        if (isDuplicate) {
            throw new CustomException(BranchErrorCode.BRANCH_NAME_DUPLICATED);
        }
    }
}
