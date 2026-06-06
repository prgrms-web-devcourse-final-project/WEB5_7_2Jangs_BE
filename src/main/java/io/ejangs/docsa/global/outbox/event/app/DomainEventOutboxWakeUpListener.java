package io.ejangs.docsa.global.outbox.event.app;

import io.ejangs.docsa.global.config.AsyncConfig;
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

    @Async(AsyncConfig.OUTBOX_WAKE_UP_EXECUTOR)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(DomainEventOutboxWakeUpEvent event) {
        relay.run();
    }
}
