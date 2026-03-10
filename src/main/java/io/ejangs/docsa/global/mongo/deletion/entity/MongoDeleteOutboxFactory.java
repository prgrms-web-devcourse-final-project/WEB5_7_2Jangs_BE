package io.ejangs.docsa.global.mongo.deletion.entity;

import jakarta.persistence.EntityManager;
import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.TriggerType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MongoDeleteOutboxFactory {

    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;
    private final EntityManager entityManager;

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
            return mongoDeleteOutboxRepository.save(newOutbox);
        } catch (DataIntegrityViolationException e) {
            // 유니크 키 충돌 시, 실패한 엔티티를 영속성 컨텍스트에서 분리합니다.
            // 같은 트랜잭션의 후속 flush에서 발생할 수 있는 부작용을 방지하기 위함
            if (entityManager.contains(newOutbox)) {
                entityManager.detach(newOutbox);
            }
            return mongoDeleteOutboxRepository
                    .findByTriggerTypeAndDomainTypeAndOriginTypeAndOriginId(
                            triggerType,
                            domainType,
                            originType,
                            normalizedOriginId
                    )
                    .orElseThrow(() -> e);
        }
    }
}
