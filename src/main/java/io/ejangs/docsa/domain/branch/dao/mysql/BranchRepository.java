package io.ejangs.docsa.domain.branch.dao.mysql;

import io.ejangs.docsa.domain.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByIdAndDocIdAndDocUserId(Long branchId, Long documentId, Long userId);

    boolean existsByFromCommitIdIn(List<Long> commitIds);
}

