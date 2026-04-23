package io.ejangs.docsa.domain.doc.entity;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.user.entity.User;
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
@Table(name = "docs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_title", columnNames = {"user_id", "title"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Doc extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "doc", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Branch> branches;

    @OneToMany(mappedBy = "doc", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Edge> edges;

    @OneToOne(mappedBy = "doc", cascade = CascadeType.ALL, orphanRemoval = true)
    private Thumbnail thumbnail;

    @Builder
    private Doc(String title, User user) {
        this.title = title;
        setUser(user);
        this.branches = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void addBranch(Branch branch) {
        this.branches.add(branch);
        if (branch.getDoc() != this) {
            branch.setDoc(this);
        }
    }

    public void addEdge(Edge edge) {
        this.edges.add(edge);
        if (edge.getDoc() != this) {
            edge.setDoc(this);
        }
    }

    public void setUser(User user) {
        this.user = user;
        if (!user.getDocs().contains(this)) {
            user.getDocs().add(this);
        }
    }

    public void removeEdge(Edge edge) {
        this.edges.remove(edge);
    }
}
