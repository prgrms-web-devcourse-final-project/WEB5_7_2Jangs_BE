package io.ejangs.docsa.global.outbox.event.dao;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutbox, Long> {

    List<DomainEventOutbox> findTop100ByStatusOrderByCreatedAtAscIdAsc(OutboxStatus status);

    List<DomainEventOutbox> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            OutboxStatus status,
            LocalDateTime updatedAt
    );

    Optional<DomainEventOutbox> findByIdAndStatus(Long id, OutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update domain_event_outbox
            set status = 'PROCESSING',
                updated_at = current_timestamp,
                version = version + 1
            where id = :outboxId
              and status = 'OPEN'
            """, nativeQuery = true)
    int claimOpenById(@Param("outboxId") Long outboxId);
}
