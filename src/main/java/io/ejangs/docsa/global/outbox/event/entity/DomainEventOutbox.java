package io.ejangs.docsa.global.outbox.event.entity;

import io.ejangs.docsa.global.outbox.BaseOutboxEntity;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "domain_event_outbox",
        indexes = {
                @Index(name = "idx_domain_event_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_domain_event_outbox_status_updated_at", columnList = "status, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainEventOutbox extends BaseOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DomainEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AggregateType aggregateType;

    @Column(nullable = false, length = 255)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    public static DomainEventOutbox open(
            DomainEventType eventType,
            AggregateType aggregateType,
            String aggregateId,
            String payload
    ) {
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.eventType = eventType;
        outbox.aggregateType = aggregateType;
        outbox.aggregateId = aggregateId;
        outbox.payload = payload;
        outbox.initOutbox();
        return outbox;
    }

    public DomainEventMessage toMessage() {
        return new DomainEventMessage(
                id,
                eventType,
                aggregateType,
                aggregateId,
                payload,
                getCreatedAt()
        );
    }

}