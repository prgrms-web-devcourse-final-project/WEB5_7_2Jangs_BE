package io.ejangs.docsa.domain.commit.entity;

import io.ejangs.docsa.domain.block.entity.Block;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "commit_block_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitBlockSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean first;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", nullable = false)
    private Commit commit;

    @OneToOne
    @JoinColumn(name = "current_block_id", nullable = false)
    private Block currentBlock;

    @OneToOne
    @JoinColumn(name = "next_block_id")
    private Block nextBlock;

    @Builder
    private CommitBlockSequence(Boolean first, Commit commit, Block currentBlock, Block nextBlock) {
        this.first = first != null;
        this.commit = commit;
        this.currentBlock = currentBlock;
        this.nextBlock = nextBlock;
    }
}
