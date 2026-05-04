package io.ejangs.docsa.global.outbox.event.app;

import io.ejangs.docsa.global.outbox.event.dto.DomainEventOutboxWakeUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DomainEventOutboxWakeUpListener {

    private final DomainEventOutboxRelay relay;

    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handle(DomainEventOutboxWakeUpEvent event) {
        relay.run(event.outboxId());
    }
}
