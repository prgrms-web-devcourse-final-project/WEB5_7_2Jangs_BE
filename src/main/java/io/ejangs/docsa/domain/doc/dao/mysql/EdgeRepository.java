package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Edge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EdgeRepository extends JpaRepository<Edge, Long> {

    List<Edge> findAllByPrevCommitIdInOrNextCommitIdIn(List<Long> commitIds, List<Long> commitIds1);

    List<Edge> findByNextCommitId(Long id);

    List<Edge> findByPrevCommitId(Long id);

    @Query("""
        SELECT e
        FROM Edge e
        WHERE e.prevCommit.id = :commitId OR e.nextCommit.id = :commitId
    """)
    List<Edge> findAllByCommitIdInPrevOrNext(@Param("commitId") Long commitId);
}
