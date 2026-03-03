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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mongo_delete_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mongo_delete_outbox_operation_target", columnNames = {"operation_type", "target_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongoDeleteOutbox extends BaseEntity {

    public enum OutboxStatus {
        OPEN,
        DONE,
        FAILED
    }

    public enum OperationType {
        DELETE_SAVE,
        DELETE_COMMIT,
        DELETE_DOC,
        DELETE_BRANCH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 64, nullable = false)
    private OperationType operationType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

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
            long targetId,
            List<String> saveIds,
            List<String> commitIds,
            List<String> blockIds
    ) {
        List<String> normalizedSaveIds = List.copyOf(saveIds == null ? List.of() : saveIds);
        List<String> normalizedCommitIds = List.copyOf(commitIds == null ? List.of() : commitIds);
        List<String> normalizedBlockIds = List.copyOf(blockIds == null ? List.of() : blockIds);

        if (normalizedSaveIds.isEmpty() && normalizedCommitIds.isEmpty() && normalizedBlockIds.isEmpty()) {
            throw new IllegalArgumentException("at least one delete target id is required");
        }

        MongoDeleteOutbox outbox = new MongoDeleteOutbox();
        outbox.operationType = operationType;
        outbox.targetId = targetId;
        outbox.saveContentIds = normalizedSaveIds;
        outbox.commitBlockSequenceIds = normalizedCommitIds;
        outbox.blockIds = normalizedBlockIds;
        outbox.status = OutboxStatus.OPEN;
        outbox.retryCount = 0;
        outbox.maxRetry = 10;
        return outbox;
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.doneAt = LocalDateTime.now();
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
