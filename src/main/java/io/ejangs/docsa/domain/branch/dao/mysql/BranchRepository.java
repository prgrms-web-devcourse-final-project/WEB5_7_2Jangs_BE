package io.ejangs.docsa.domain.branch.dao.mysql;

import io.ejangs.docsa.domain.branch.entity.Branch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByIdAndDocIdAndDocUserId(Long branchId, Long documentId, Long userId);

    boolean existsByFromCommitIdIn(List<Long> commitIds);

    @Query("""
        SELECT COUNT(b) > 0
        FROM Branch b
        WHERE b.rootCommit.id = :commitId OR b.fromCommit.id = :commitId
    """)
    boolean existsByRootCommitIdOrFromCommitId(@Param("commitId") Long commitId);
}

