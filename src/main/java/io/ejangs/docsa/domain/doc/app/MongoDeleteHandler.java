package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dto.DocDeleteMongoIdsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDeleteHandler {

    private final MongoDeleteRetryService retryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeleteEvent(DocDeleteMongoIdsDto deleteMongoIdsDto) {
        retryService.deleteMongoData(deleteMongoIdsDto);
    }
}
