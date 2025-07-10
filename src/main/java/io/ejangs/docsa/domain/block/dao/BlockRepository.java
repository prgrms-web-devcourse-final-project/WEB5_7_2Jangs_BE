package io.ejangs.docsa.domain.block.dao;

import io.ejangs.docsa.domain.block.entity.Block;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    @Query("""
            SELECT b 
            FROM Block b 
            WHERE b.uniqueId = :uniqueId 
            ORDER BY b.id DESC
            """)
    Optional<Block> findLatestByUniqueId(@Param("uniqueId") String uniqueId);
}
