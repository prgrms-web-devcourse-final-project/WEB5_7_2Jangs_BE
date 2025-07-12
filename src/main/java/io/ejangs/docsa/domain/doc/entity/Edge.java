package io.ejangs.docsa.domain.doc.entity;

import io.ejangs.docsa.domain.commit.entity.Commit;
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
@Table(name = "edges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Edge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Doc doc;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_commit_id", nullable = false)
    private Commit prevCommit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_commit_id", nullable = false)
    private Commit nextCommit;

    @Builder
    private Edge(Doc doc, Commit prevCommit, Commit nextCommit) {
        this.doc = doc;
        this.prevCommit = prevCommit;
        this.nextCommit = nextCommit;
    }
}
