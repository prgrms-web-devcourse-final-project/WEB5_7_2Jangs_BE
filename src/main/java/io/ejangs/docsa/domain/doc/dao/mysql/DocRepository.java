package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Doc;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocRepository extends JpaRepository<Doc, Long> {

    @Query("""
                SELECT DISTINCT d
                FROM Doc d
                LEFT JOIN FETCH d.branches b
                LEFT JOIN FETCH b.commits
                LEFT JOIN FETCH d.edges
                WHERE d.id = :id
            """)
    Optional<Doc> findByIdWithBranchesAndEdges(@Param("id") Long id);

    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);

    boolean existsByIdAndUserId(Long Id, Long userId);

    Page<Doc> findAllByUserId(Long userId, Pageable pageable);

    @Query("""
            SELECT d
            FROM Doc d
            WHERE d.user.id = :userId AND d.title LIKE %:title%
            """)
    Page<Doc> searchDocByTitle_LIKE(@Param("title") String title, @Param("userId") Long userId,
            Pageable pageable);

    @Query(
            value = "SELECT * FROM docs WHERE user_id = :userId AND MATCH(title) AGAINST (:title IN NATURAL LANGUAGE MODE)",
            countQuery = "SELECT COUNT(*) FROM docs WHERE user_id = :userId AND MATCH(title) AGAINST (:title IN NATURAL LANGUAGE MODE)",
            nativeQuery = true
    )
    Page<Doc> searchDocByTitle_FULLTEXT(@Param("title") String title, @Param("userId") Long userId,
            Pageable pageable);
}
