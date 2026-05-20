package io.ejangs.docsa.global.outbox.event.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.global.outbox.event.dao.DomainEventOutboxRepository;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventOutboxWakeUpEvent;
import io.ejangs.docsa.global.outbox.event.entity.DomainEventOutbox;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventOutboxPublisher {

    private final DomainEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(DomainEventType eventType, AggregateType aggregateType, Long aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            DomainEventOutbox outbox = repository.save(DomainEventOutbox.open(
                    eventType,
                    aggregateType,
                    String.valueOf(aggregateId),
                    json
            ));

            applicationEventPublisher.publishEvent(new DomainEventOutboxWakeUpEvent(outbox.getId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Domain event payload serialize failed", e);
        }
    }
}
