package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Doc;
import java.util.List;
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

    //페이지네이션 적용안된 사이드바 문서조회 임시조치용
    List<Doc> findAllByUserId(Long userId);
}
