package io.ejangs.docsa.domain.branch.entity;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;
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
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "branches",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "document_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Doc doc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_commit_id")
    private Commit fromCommit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_commit_id")
    private Commit rootCommit;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leaf_commit_id")
    private Commit leafCommit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marge_target_commit_id")
    private Commit mergeTargetCommit;

    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Commit> commits;

    @OneToOne(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private Save save;

    @Builder
    private Branch(String name, Doc doc, Commit fromCommit) {
        this.name = name;
        setDoc(doc);
        this.fromCommit = fromCommit;
        this.commits = new ArrayList<>();
    }

    public void updateLeafCommit(Commit leafCommit) {
        this.leafCommit = leafCommit;
    }

    public void updateRootCommit(Commit commit) {
        if (this.rootCommit == null) {
            this.rootCommit = commit;
        }
    }

    public void updateMergeTargetCommit(Commit mergeTargetCommit) {
        this.mergeTargetCommit = mergeTargetCommit;
    }

    public void setSave(Save save) {
        this.save = save;
        if (save.getBranch() != this) {
            save.setBranch(this);
        }
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
        if (!doc.getBranches().contains(this)) {
            doc.getBranches().add(this);
        }
    }

    public void addCommit(Commit commit) {
        if (!commits.contains(commit)) {
            this.commits.add(commit);
            if (commit.getBranch() != this) {
                commit.setBranch(this);
            }
        }
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void removeCommit(Commit commit) {
        this.commits.remove(commit);
    }

    public void removeSave() {
        this.save = null;
    }
}
