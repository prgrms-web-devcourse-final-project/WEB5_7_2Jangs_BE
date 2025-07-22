package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Doc;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocRepository extends JpaRepository<Doc, Long> {

    // Doc과 Branch, Commit만 fetch join
    @Query("""
        SELECT DISTINCT d
        FROM Doc d
        LEFT JOIN FETCH d.branches b
        LEFT JOIN FETCH b.commits
        WHERE d.id = :id
    """)
    Optional<Doc> findByIdWithBranchesAndCommits(@Param("id") Long id);

    // Doc과 Edge만 fetch join
    @Query("""
        SELECT DISTINCT d
        FROM Doc d
        LEFT JOIN FETCH d.edges
        WHERE d.id = :id
    """)
    Optional<Doc> findByIdWithEdges(@Param("id") Long id);

    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);

    boolean existsByIdAndUserId(Long Id, Long userId);

    List<Doc> findAllByUserId(Long userId);
}
