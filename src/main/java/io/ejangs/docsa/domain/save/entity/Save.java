package io.ejangs.docsa.domain.save.entity;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "saves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Save extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String saveMongoId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Builder
    private Save(Branch branch) {
        this.branch = branch;
    }

    public void updateSaveMongoId(String saveMongoId) {
        this.saveMongoId = saveMongoId;
    }
}
