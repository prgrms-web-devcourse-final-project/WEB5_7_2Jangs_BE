package io.ejangs.docsa.global.outbox.event.app.dispatcher;

import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;

public interface DomainEventDispatcher {

    void dispatch(DomainEventMessage message);
}