package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocRepository extends JpaRepository<Doc, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse(
                  d.id, d.title, d.createdAt, d.updatedAt
                )
                FROM Doc d
                WHERE d.user.id = :userId
                ORDER BY d.updatedAt DESC
            """)
    List<DocListSimpleResponse> getSimpleList(@Param("userId") Long userId);

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

    boolean existsByIdAndUserId(Long id, Long userId);
}
