package io.ejangs.docsa.global.mongo.deletion.entity;

import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mongo_delete_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mongo_delete_outbox_operation_key", columnNames = {"operation_key"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongoDeleteOutbox extends BaseEntity {

    public enum OutboxStatus {
        OPEN,
        PROCESSING,
        DONE,
        FAILED
    }

    public enum OperationType {
        DELETE_SAVE,
        DELETE_COMMIT,
        DELETE_DOC,
        DELETE_BRANCH
    }

    public enum OperationSource {
        USER_REQUEST,
        COMPENSATION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 64, nullable = false)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_source", length = 64, nullable = false)
    private OperationSource operationSource;

    @Column(name = "operation_key", length = 255, nullable = false)
    private String operationKey;

    @Column(name = "target_id", nullable = true)
    private Long domainId;

    @Column(name = "target_mongo_id", nullable = true)
    private String targetMongoId;

    @ElementCollection
    @CollectionTable(name = "mongo_outbox_save_ids", joinColumns = @JoinColumn(name = "outbox_id"))
    @Column(name = "save_id")
    private List<String> saveContentIds;

    @ElementCollection
    @CollectionTable(name = "mongo_outbox_commit_ids", joinColumns = @JoinColumn(name = "outbox_id"))
    @Column(name = "commit_id")
    private List<String> commitBlockSequenceIds;

    @ElementCollection
    @CollectionTable(name = "mongo_outbox_block_ids", joinColumns = @JoinColumn(name = "outbox_id"))
    @Column(name = "block_id")
    private List<String> blockIds;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer maxRetry;

    private LocalDateTime doneAt;

    @Column(length = 2000)
    private String lastError;

    @Version
    private Long version;

    public static MongoDeleteOutbox open(
            OperationType operationType,
            OperationSource operationSource,
            String operationKey,
            Long targetId,
            String targetMongoId,
            List<String> saveIds,
            List<String> commitIds,
            List<String> blockIds
    ) {

        MongoDeleteOutbox outbox = new MongoDeleteOutbox();
        outbox.operationType = operationType;
        outbox.operationSource = operationSource;
        outbox.operationKey = operationKey;
        outbox.targetMongoId = targetMongoId;
        outbox.domainId = targetId;
        outbox.saveContentIds = saveIds;
        outbox.commitBlockSequenceIds = commitIds;
        outbox.blockIds = blockIds;
        outbox.status = OutboxStatus.OPEN;
        outbox.retryCount = 0;
        outbox.maxRetry = 10;
        return outbox;
    }

    public static String buildOperationKey(
            OperationSource operationSource,
            OperationType operationType,
            String refType,
            String refValue
    ) {
        return operationSource + ":" + operationType + ":" + refType + ":" + refValue;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.doneAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markRetry(String errorMessage) {
        this.retryCount = this.retryCount + 1;
        this.lastError = errorMessage;

        if (this.retryCount >= this.maxRetry) {
            this.status = OutboxStatus.FAILED;
            return;
        }

        this.status = OutboxStatus.OPEN;
    }

}
