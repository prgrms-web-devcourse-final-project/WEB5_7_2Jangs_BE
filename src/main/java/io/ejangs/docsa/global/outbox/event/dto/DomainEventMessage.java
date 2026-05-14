package io.ejangs.docsa.global.outbox.event.dto;

import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import java.time.LocalDateTime;

public record DomainEventMessage(
        Long eventId,
        DomainEventType eventType,
        AggregateType aggregateType,
        String aggregateId,
        String payload,
        LocalDateTime occurredAt
) {
}