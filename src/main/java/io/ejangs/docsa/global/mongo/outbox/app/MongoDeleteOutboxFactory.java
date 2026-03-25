package io.ejangs.docsa.global.mongo.outbox.app;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.mongo.outbox.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoDeleteOutboxFactory {

    private final MongoDeleteOutboxCreateService mongoDeleteOutboxCreateService;
    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    public MongoDeleteOutbox create(
            TriggerType triggerType,
            DomainType domainType,
            OriginType originType,
            Long originId,
            MongoIdsDto ids
    ) {
        Objects.requireNonNull(originId, "originId is required");
        if (originId <= 0) {
            throw new IllegalArgumentException("originId must be positive");
        }
        return create(triggerType, domainType, originType, String.valueOf(originId), ids);
    }

    public MongoDeleteOutbox create(
            TriggerType triggerType,
            DomainType domainType,
            OriginType originType,
            String originId,
            MongoIdsDto ids
    ) {

        Objects.requireNonNull(triggerType, "triggerType is required");
        Objects.requireNonNull(domainType, "domainType is required");
        Objects.requireNonNull(originType, "originType is required");
        Objects.requireNonNull(ids, "mongo ids is required");

        if (ids.saveContentsIds().isEmpty()
                && ids.commitBlockSequenceIds().isEmpty()
                && ids.blockIds().isEmpty()) {
            return null;
        }

        String normalizedOriginId = originId == null ? "" : originId.trim();
        if (normalizedOriginId.isBlank()) {
            throw new IllegalArgumentException("originId is required");
        }

        MongoDeleteOutbox existing = mongoDeleteOutboxRepository
                .findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
                        triggerType,
                        domainType,
                        originType,
                        normalizedOriginId
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        MongoDeleteOutbox newOutbox = MongoDeleteOutbox.open(
                triggerType,
                domainType,
                originType,
                normalizedOriginId,
                ids.saveContentsIds(),
                ids.commitBlockSequenceIds(),
                ids.blockIds()
        );

        try {
            mongoDeleteOutboxCreateService.tryCreate(newOutbox);
        } catch (DataIntegrityViolationException e) {
            return mongoDeleteOutboxRepository
                    .findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
                            triggerType,
                            domainType,
                            originType,
                            normalizedOriginId
                    )
                    .orElseThrow(() -> new CustomException(DatabaseErrorCode.DATABASE_ERROR));

        }

        return newOutbox;
    }
}
