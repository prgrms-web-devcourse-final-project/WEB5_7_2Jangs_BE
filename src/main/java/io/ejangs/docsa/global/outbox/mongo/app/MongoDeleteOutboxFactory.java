package io.ejangs.docsa.global.outbox.mongo.app;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.TriggerType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoDeleteOutboxFactory {

    private final MongoDeleteOutboxCreateService mongoDeleteOutboxCreateService;
    private final MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    /*
     * originId는 outbox 중복 생성을 막기 위한 기준 ID다.
     * 실제 값의 의미는 각 생성 메서드의 파라미터 이름으로 표현한다.
     * - 삭제 outbox: 삭제 요청의 기준이 된 MySQL id
     * - 보상 outbox: Mongo에 먼저 생성된 데이터의 id
     */
    public MongoDeleteOutbox createDocDelete(Long docId, MongoIdsDto ids) {
        return create(TriggerType.DELETE, DomainType.DOC, docId, ids);
    }

    public MongoDeleteOutbox createBranchDelete(Long branchId, MongoIdsDto ids) {
        return create(TriggerType.DELETE, DomainType.BRANCH, branchId, ids);
    }

    public MongoDeleteOutbox createCommitDelete(Long commitId, MongoIdsDto ids) {
        return create(TriggerType.DELETE, DomainType.COMMIT, commitId, ids);
    }

    public MongoDeleteOutbox createDocCreateCompensation(String saveContentId, MongoIdsDto ids) {
        return create(TriggerType.COMPENSATE, DomainType.DOC, saveContentId, ids);
    }

    public MongoDeleteOutbox createBranchCreateCompensation(String saveContentId, MongoIdsDto ids) {
        return create(TriggerType.COMPENSATE, DomainType.BRANCH, saveContentId, ids);
    }

    public MongoDeleteOutbox createCommitCreateCompensation(String commitBlockSequenceId, MongoIdsDto ids) {
        return create(TriggerType.COMPENSATE, DomainType.COMMIT, commitBlockSequenceId, ids);
    }

    public MongoDeleteOutbox createMergeCompensation(String saveMongoId, MongoIdsDto ids) {
        return create(TriggerType.COMPENSATE, DomainType.MERGE, saveMongoId, ids);
    }

    private MongoDeleteOutbox create(
            TriggerType triggerType,
            DomainType domainType,
            Long originId,
            MongoIdsDto ids
    ) {
        Objects.requireNonNull(originId, "originId is required");
        if (originId <= 0) {
            throw new IllegalArgumentException("originId must be positive");
        }
        return create(triggerType, domainType, String.valueOf(originId), ids);
    }

    private MongoDeleteOutbox create(
            TriggerType triggerType,
            DomainType domainType,
            String originId,
            MongoIdsDto ids
    ) {

        Objects.requireNonNull(triggerType, "triggerType is required");
        Objects.requireNonNull(domainType, "domainType is required");
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
                .findByTriggerTypeAndDomainTypeAndOriginId(
                        triggerType,
                        domainType,
                        normalizedOriginId
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        MongoDeleteOutbox newOutbox = MongoDeleteOutbox.open(
                triggerType,
                domainType,
                normalizedOriginId,
                ids.saveContentsIds(),
                ids.commitBlockSequenceIds(),
                ids.blockIds()
        );

        try {
            mongoDeleteOutboxCreateService.tryCreate(newOutbox);
        } catch (DataIntegrityViolationException e) {
            return mongoDeleteOutboxRepository
                    .findByTriggerTypeAndDomainTypeAndOriginId(
                            triggerType,
                            domainType,
                            normalizedOriginId
                    )
                    .orElseThrow(() -> new CustomException(DatabaseErrorCode.DATABASE_ERROR));

        }

        return newOutbox;
    }
}
