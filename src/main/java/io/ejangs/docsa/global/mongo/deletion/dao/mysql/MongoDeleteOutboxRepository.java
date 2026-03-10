package io.ejangs.docsa.global.mongo.deletion.dao.mysql;

import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.TriggerType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MongoDeleteOutboxRepository extends JpaRepository<MongoDeleteOutbox, Long> {

    List<MongoDeleteOutbox> findTop100ByStatusOrderByCreatedAtAsc(MongoDeleteOutbox.OutboxStatus status);

    List<MongoDeleteOutbox> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            MongoDeleteOutbox.OutboxStatus status,
            LocalDateTime updatedAt
    );

    Optional<MongoDeleteOutbox> findByIdAndStatus(Long id, MongoDeleteOutbox.OutboxStatus status);

    Optional<MongoDeleteOutbox> findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
            TriggerType triggerType,
            DomainType domainType,
            OriginType originType,
            String originId
    );
}
