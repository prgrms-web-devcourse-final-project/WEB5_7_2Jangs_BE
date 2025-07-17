package io.ejangs.docsa.global.mongoDeleteSystem.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mongo_delete_failures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MongoDeleteFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mongo_failure_save_ids", joinColumns = @JoinColumn(name = "failure_id"))
    @Column(name = "save_id")
    private List<String> saveContentIds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mongo_failure_commit_ids", joinColumns = @JoinColumn(name = "failure_id"))
    @Column(name = "commit_id")
    private List<String> commitBlockSequenceIds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mongo_failure_block_ids", joinColumns = @JoinColumn(name = "failure_id"))
    @Column(name = "block_id")
    private List<String> blockIds;

    private Boolean resolved = false;

    private LocalDateTime createdAt;

    @Builder
    private MongoDeleteFailure(List<String> saveContentIds, List<String> commitBlockSequenceIds,
            List<String> blockIds) {
        this.saveContentIds = saveContentIds;
        this.commitBlockSequenceIds = commitBlockSequenceIds;
        this.blockIds = blockIds;
        this.createdAt = LocalDateTime.now();
        this.resolved = false;
    }

    public void markResolved() {
        this.resolved = true;
    }
}