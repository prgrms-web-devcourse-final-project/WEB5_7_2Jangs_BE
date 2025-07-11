package io.ejangs.docsa.domain.branch.entity;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.document.entity.Document;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "branches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_commit_id")
    private Commit fromCommit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_commit_id")
    private Commit rootCommit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leaf_commit_id")
    private Commit leafCommit;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Commit> commits;

    @Builder
    private Branch(String name, Document document, Commit fromCommit) {
        this.name = name;
        this.document = document;
        this.fromCommit = fromCommit;
        this.commits = new ArrayList<>();
    }

    public void updateLeafCommit(Commit leafCommit) {
        this.leafCommit = leafCommit;
    }
}
