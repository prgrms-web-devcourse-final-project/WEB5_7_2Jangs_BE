package io.ejangs.docsa.global.outbox.s3.dao;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.s3.entity.S3DeleteOutbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface S3DeleteOutboxRepository extends JpaRepository<S3DeleteOutbox, Long> {

    List<S3DeleteOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<S3DeleteOutbox> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            OutboxStatus status,
            LocalDateTime updatedAt
    );

    Optional<S3DeleteOutbox> findByIdAndStatus(Long id, OutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update s3_delete_outbox
            set status = 'PROCESSING',
                updated_at = current_timestamp,
                version = version + 1
            where id = :outboxId
              and status = 'OPEN'
            """, nativeQuery = true)
    int claimOpenById(@Param("outboxId") Long outboxId);

    @Modifying
    @Query(value = """
            insert into s3_delete_outbox
                (created_at, updated_at, image_id, object_key, status, retry_count, max_retry, version)
            values
                (now(6), now(6), :imageId, :objectKey, 'OPEN', 0, 10, 0)
            on duplicate key update
                object_key = object_key
            """, nativeQuery = true)
    void insertOpenIfAbsent(@Param("imageId") Long imageId, @Param("objectKey") String objectKey);
}
