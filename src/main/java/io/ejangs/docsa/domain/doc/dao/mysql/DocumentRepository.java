package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Doc, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.DocumentListSimpleResponse(
                  d.id, d.title, d.createdAt, d.updatedAt
                )
                FROM Doc d
                WHERE d.user.id = :userId
                ORDER BY d.updatedAt DESC
            """)
    List<DocumentListSimpleResponse> getSimpleList(@Param("userId") Long userId);

    Optional<Doc> getDocByIdAndUserId(Long userId, Long id);

    Boolean existsByUserIdAndTitle(Long userId, String title);

}

