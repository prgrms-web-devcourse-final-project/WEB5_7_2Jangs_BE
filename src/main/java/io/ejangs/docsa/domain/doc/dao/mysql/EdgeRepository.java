package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto;
import io.ejangs.docsa.domain.doc.entity.Edge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EdgeRepository extends JpaRepository<Edge, Integer> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto(
                    e.prevCommit.id, e.nextCommit.id
                )
                FROM Edge e
                WHERE e.doc.id = :docId
            """)
    List<GraphEdgeDto> findEdgesByDocId(@Param("docId") Long docId);

    List<Edge> findAllByPrevCommitIdInOrNextCommitIdIn(List<Long> commitIds, List<Long> commitIds1);

    List<Edge> findByNextCommitId(Long id);

}
