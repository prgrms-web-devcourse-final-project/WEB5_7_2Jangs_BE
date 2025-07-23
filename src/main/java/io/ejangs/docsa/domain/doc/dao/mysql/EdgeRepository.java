package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Edge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EdgeRepository extends JpaRepository<Edge, Integer> {

    List<Edge> findAllByPrevCommitIdInOrNextCommitIdIn(List<Long> commitIds, List<Long> commitIds1);

    List<Edge> findByNextCommitId(Long id);

}
