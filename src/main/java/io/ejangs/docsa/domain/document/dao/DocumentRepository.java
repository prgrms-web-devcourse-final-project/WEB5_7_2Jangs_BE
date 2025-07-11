package io.ejangs.docsa.domain.document.dao;

import io.ejangs.docsa.domain.document.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.document.dto.DocumentListSimpleResponse(
                  d.id, d.title, d.createdAt, d.updatedAt
                )
                FROM Document d
                WHERE d.user.id = :userId
                ORDER BY d.updatedAt DESC
            """)
    List<DocumentListSimpleResponse> getSimpleDocumentList(@Param("userId") Long userId);
}

