package io.ejangs.docsa.domain.branch.dao.mysql;

import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    @Query("""
    SELECT new io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto(
        b.id,
        b.name,
        b.createdAt,
        b.fromCommit.id,
        b.mergeTargetCommit.id,
        b.rootCommit.id,
        b.leafCommit.id,
        (
            SELECT s.id FROM Save s WHERE s.branch.id = b.id
        )
    )
    FROM Branch b
    WHERE b.doc.id = :docId
""")
    List<BranchGraphDto> findBranchGraphDtoList(@Param("docId") Long docId);

    boolean existsByIdAndDocIdAndDocUserId(Long branchId, Long documentId, Long userId);

    boolean existsByFromCommitIdIn(List<Long> commitIds);

    @Query("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM Branch b
                    WHERE b.fromCommit.id = :commitId
                    OR b.mergeTargetCommit.id = :commitId
                ) THEN true ELSE false END
            """)
    boolean existsByFromOrMergeTargetCommitId(@Param("commitId") Long commitId);

    boolean existsByDocIdAndName(Long docId, String name);
}
