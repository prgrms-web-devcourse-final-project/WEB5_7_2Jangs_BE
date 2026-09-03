package io.ejangs.docsa.domain.commit.dao.mysql;

import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitRepository extends JpaRepository<Commit, Long> {

    @EntityGraph(attributePaths = {"branch", "branch.doc"})
    Optional<Commit> findWithBranchAndDocById(Long commitId);

    @Query("""
                SELECT new io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto(
                    c.id, c.branch.id, c.title, c.description, c.createdAt
                )
                FROM Commit c
                WHERE c.branch.doc.id = :docId
            """)
    List<CommitGraphDto> getCommitGraphList(@Param("docId") Long docId);


    @Query("""
                select count(c.id)
                from Commit c
                join c.branch b
                join b.doc d
                where c.id in :commitIds
                  and d.id = :documentId
                  and d.user.id = :userId
            """)
    long countCommitsInOwnedDoc(
            @Param("commitIds") List<Long> commitIds,
            @Param("documentId") Long documentId,
            @Param("userId") Long userId
    );
}
