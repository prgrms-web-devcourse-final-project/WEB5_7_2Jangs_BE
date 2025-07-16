package io.ejangs.docsa.domain.save.dao.mysql;

import io.ejangs.docsa.domain.save.entity.Save;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaveRepository extends JpaRepository<Save, Long> {

    Optional<Save> findByBranchId(Long branchId);

    @Query("""
                SELECT COUNT(s) > 0
                FROM Save s
                JOIN s.branch b
                JOIN b.doc d
                JOIN d.user u
                WHERE s.id = :saveId AND d.id = :documentId AND u.id = :userId
            """)
    boolean validateSaveOwnership(Long saveId, Long documentId, Long userId);
}