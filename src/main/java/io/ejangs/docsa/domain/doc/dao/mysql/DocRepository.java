package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocRepository extends JpaRepository<Doc, Long> {

    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);

    boolean existsByIdAndUserId(Long Id, Long userId);

    @EntityGraph(attributePaths = {"thumbnail", "thumbnail.currentImage"})
    Page<Doc> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"thumbnail", "thumbnail.currentImage"})
    @Query("""
            SELECT d
            FROM Doc d
            WHERE d.user.id = :userId AND d.title LIKE CONCAT('%', :title, '%')
            """)
    Page<Doc> searchDocByTitle(@Param("title") String title, @Param("userId") Long userId,
            Pageable pageable);

}
