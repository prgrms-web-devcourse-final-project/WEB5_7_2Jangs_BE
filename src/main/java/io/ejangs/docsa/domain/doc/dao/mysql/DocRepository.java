package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocRepository extends JpaRepository<Doc, Long> {

    @Query("""
                SELECT new io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse(d.title)
                FROM Doc d
                WHERE d.id = :docId
            """)
    Optional<DocTitleOnlyResponse> findTitleOnlyById(@Param("docId") Long docId);


    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitle(Long userId, String title);

    boolean existsByIdAndUserId(Long Id, Long userId);

    List<Doc> findAllByUserId(Long userId);
}
