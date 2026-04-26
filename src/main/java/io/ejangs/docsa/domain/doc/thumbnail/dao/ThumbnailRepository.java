package io.ejangs.docsa.domain.doc.thumbnail.dao;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {

    Optional<Thumbnail> findByDocId(Long docId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from Thumbnail t
            where t.doc.id = :docId
            """)
    Optional<Thumbnail> findByDocIdForUpdate(@Param("docId") Long docId);


    @Query("""
            select t
            from Thumbnail t
            left join fetch t.currentImage
            where t.doc.id in :docIds
            """)
    List<Thumbnail> findAllByDocIdInWithCurrentImage(@Param("docIds") Collection<Long> docIds);
}
