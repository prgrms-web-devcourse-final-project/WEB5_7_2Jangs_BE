package io.ejangs.docsa.global.saga.create.entity;

import io.ejangs.docsa.global.common.BaseEntity;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "mongo_create_operation",
        indexes = @Index(
                name = "idx_mongo_create_operation_status_updated_at",
                columnList = "status, updated_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongoCreateOperation extends BaseEntity {

    @Id
    @Column(name = "operation_id", length = 36, nullable = false, updatable = false)
    private String operationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 32, nullable = false, updatable = false)
    private MongoCreateOperationType operationType;

    @Column(name = "request_hash", length = 64, nullable = false, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MongoCreateOperationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mongo_ids", nullable = false, updatable = false, columnDefinition = "json")
    private MongoIdsDto mongoIds;

    @Column(name = "result_entity_id")
    private Long resultEntityId;

    @Column(name = "result_save_id")
    private Long resultSaveId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Version
    private Long version;

    public static MongoCreateOperation pending(
            String operationId,
            Long userId,
            MongoCreateOperationType operationType,
            String requestHash,
            MongoIdsDto mongoIds
    ) {
        MongoCreateOperation operation = new MongoCreateOperation();
        operation.operationId = operationId;
        operation.userId = userId;
        operation.operationType = operationType;
        operation.requestHash = requestHash;
        operation.status = MongoCreateOperationStatus.PENDING;
        operation.mongoIds = mongoIds;
        return operation;
    }

    public MongoIdsDto mongoIds() {
        return mongoIds;
    }

    public void complete(Long resultEntityId, Long resultSaveId) {
        requireStatus(MongoCreateOperationStatus.PENDING);
        this.status = MongoCreateOperationStatus.COMPLETED;
        this.resultEntityId = resultEntityId;
        this.resultSaveId = resultSaveId;
        this.completedAt = LocalDateTime.now();
        this.lastError = null;
        updateTimestamp();
    }

    public void startCompensating(String errorMessage) {
        requireStatus(MongoCreateOperationStatus.PENDING);
        this.status = MongoCreateOperationStatus.COMPENSATING;
        this.lastError = errorMessage;
        updateTimestamp();
    }

    public void markCompensated() {
        requireStatus(MongoCreateOperationStatus.COMPENSATING);
        this.status = MongoCreateOperationStatus.COMPENSATED;
        this.completedAt = LocalDateTime.now();
        updateTimestamp();
    }

    public void markCompensationFailed(String errorMessage) {
        requireStatus(MongoCreateOperationStatus.COMPENSATING);
        this.status = MongoCreateOperationStatus.FAILED;
        this.lastError = errorMessage;
        this.completedAt = LocalDateTime.now();
        updateTimestamp();
    }

    private void requireStatus(MongoCreateOperationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Mongo create operation status must be " + expected + " but was " + status
            );
        }
    }
}
