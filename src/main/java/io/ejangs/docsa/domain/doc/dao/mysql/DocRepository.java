package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.doc.dto.EdgeDto;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocRepository extends JpaRepository<Doc, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse(d.id, d.title)
                FROM Doc d
                WHERE d.id = :docId
            """)
    Optional<DocTitleOnlyResponse> findTitleOnlyById(@Param("docId") Long docId);

    @Query("""
                SELECT new io.ejangs.docsa.domain.branch.dto.BranchDto(
                    b.id, b.name, b.createdAt, b.fromCommit.id, b.rootCommit.id, b.leafCommit.id, b.save.id
                )
                FROM Branch b
                WHERE b.doc.id = :docId
            """)
    List<BranchDto> findBranchesByDocId(@Param("docId") Long docId);

    @Query("""
                SELECT new io.ejangs.docsa.domain.commit.dto.CommitDto(
                    c.id, c.branch.id, c.title, c.description, c.createdAt
                )
                FROM Commit c
                WHERE c.branch.doc.id = :docId
            """)
    List<CommitDto> findCommitsByDocId(@Param("docId") Long docId);

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.EdgeDto(
                    e.prevCommit.id, e.nextCommit.id
                )
                FROM Edge e
                WHERE e.doc.id = :docId
            """)
    List<EdgeDto> findEdgesByDocId(@Param("docId") Long docId);

    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);

    boolean existsByIdAndUserId(Long Id, Long userId);

    List<Doc> findAllByUserId(Long userId);
}
