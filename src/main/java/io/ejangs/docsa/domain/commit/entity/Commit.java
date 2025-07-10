package io.ejangs.docsa.domain.commit.entity;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "commits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Commit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommitBlockSequence> commitBlocks;

    @Builder
    private Commit(String title, String description, Branch branch,
            List<CommitBlockSequence> commitBlocks) {
        this.title = title;
        this.description = description;
        this.branch = branch;
        this.commitBlocks = commitBlocks;
    }

    public void updateCommitBlocks(List<CommitBlockSequence> commitBlocks) {
        this.commitBlocks = commitBlocks;
    }
}
