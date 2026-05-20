package io.ejangs.docsa.domain.branch.dao.mysql;

import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dto.LatestSaveIdDto;
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

    @Query("""
            SELECT new io.ejangs.docsa.domain.doc.dto.LatestSaveIdDto(
                b.doc.id,
                s.id
            )
            FROM Branch b
            JOIN b.save s
            WHERE b.doc.id IN :docIds
            AND NOT EXISTS (
                SELECT 1
                FROM Branch newer
                WHERE newer.doc.id = b.doc.id
                AND (
                    newer.updatedAt > b.updatedAt
                    OR (newer.updatedAt = b.updatedAt AND newer.id > b.id)
                )
            )
            """)
    List<LatestSaveIdDto> findLatestSaveIdsByDocIds(@Param("docIds") List<Long> docIds);

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
