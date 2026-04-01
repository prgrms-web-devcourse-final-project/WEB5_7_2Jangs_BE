package io.ejangs.docsa.global.mongo.outbox.app;

import io.ejangs.docsa.global.mongo.outbox.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MongoDeleteOutboxCreateService {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Transactional
    public void tryCreate(MongoDeleteOutbox newOutbox) {
        mongoDeleteOutboxRepository.saveAndFlush(newOutbox);
    }
}
