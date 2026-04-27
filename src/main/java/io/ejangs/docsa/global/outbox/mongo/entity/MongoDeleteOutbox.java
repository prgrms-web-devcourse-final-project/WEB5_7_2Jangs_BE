package io.ejangs.docsa.global.outbox.mongo.entity;

import io.ejangs.docsa.global.outbox.BaseOutboxEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mongo_delete_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mongo_delete_outbox_trigger_domain_origin",
                        columnNames = {"trigger_type", "domain_type", "origin_type", "origin_id"}
                )
        },
        indexes = {
                @Index(name = "idx_mongo_delete_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_mongo_delete_outbox_status_updated_at", columnList = "status, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongoDeleteOutbox extends BaseOutboxEntity {

    public enum TriggerType {
        DELETE,
        COMPENSATE
    }

    public enum DomainType {
        DOC,
        BRANCH,
        COMMIT,
        MERGE,
        SAVE
    }

    public enum OriginType {
        DOC_ID,
        BRANCH_ID,
        COMMIT_ID,
        SAVE_ID,
        SAVE_CONTENT_ID,
        CBS_ID
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 64, nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", length = 64, nullable = false)
    private DomainType domainType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", length = 64, nullable = false)
    private OriginType originType;

    @Column(name = "origin_id", length = 255, nullable = false)
    private String originId;

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

    public static MongoDeleteOutbox open(
            TriggerType triggerType,
            DomainType domainType,
            OriginType originType,
            String originId,
            List<String> saveIds,
            List<String> commitIds,
            List<String> blockIds
    ) {

        MongoDeleteOutbox outbox = new MongoDeleteOutbox();
        outbox.triggerType = triggerType;
        outbox.domainType = domainType;
        outbox.originType = originType;
        outbox.originId = originId;
        outbox.saveContentIds = saveIds;
        outbox.commitBlockSequenceIds = commitIds;
        outbox.blockIds = blockIds;
        outbox.initOutbox();
        return outbox;
    }
}
