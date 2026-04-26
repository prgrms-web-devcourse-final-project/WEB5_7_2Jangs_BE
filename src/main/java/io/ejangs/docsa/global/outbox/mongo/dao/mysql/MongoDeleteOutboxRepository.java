package io.ejangs.docsa.global.outbox.mongo.dao.mysql;

import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.TriggerType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MongoDeleteOutboxRepository extends JpaRepository<MongoDeleteOutbox, Long> {

    List<MongoDeleteOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<MongoDeleteOutbox> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            OutboxStatus status,
            LocalDateTime updatedAt
    );

    Optional<MongoDeleteOutbox> findByIdAndStatus(Long id, OutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update mongo_delete_outbox
            set status = 'PROCESSING',
                updated_at = current_timestamp,
                version = version + 1
            where id = :outboxId
              and status = 'OPEN'
            """, nativeQuery = true)
    int claimOpenById(@Param("outboxId") Long outboxId);

    Optional<MongoDeleteOutbox> findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
            TriggerType triggerType,
            DomainType domainType,
            OriginType originType,
            String originId
    );
}
