package io.ejangs.docsa.global.outbox.event.app.dispatcher;

import io.ejangs.docsa.domain.doc.readmodel.app.DocListProjector;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalDomainEventDispatcher implements DomainEventDispatcher {

    private final DocListProjector docListProjector;

    @Override
    public void dispatch(DomainEventMessage message) {
        switch (message.aggregateType()) {
            case DOC -> docListProjector.project(message);
        }
    }
}