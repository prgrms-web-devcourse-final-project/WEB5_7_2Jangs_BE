package io.ejangs.docsa.domain.commit.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitRepository extends JpaRepository<Commit, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.graph.GraphCommitDto(
                    c.id, c.branch.id, c.title, c.description, c.createdAt
                )
                FROM Commit c
                WHERE c.branch.doc.id = :docId
            """)
    List<CommitGraphDto> findCommitsByDocId(@Param("docId") Long docId);

}

