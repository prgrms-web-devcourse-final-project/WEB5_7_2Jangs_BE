package io.ejangs.docsa.domain.save.entity;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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

    private String saveMongoId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Builder
    private Save(Branch branch, String saveMongoId) {
        this.branch = branch;
        branch.setSave(this);
        this.saveMongoId = saveMongoId;
    }

    public void updateSaveMongoId(String saveMongoId) {
        this.saveMongoId = saveMongoId;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
        if (branch.getSave() != this) {
            branch.setSave(this);
        }
    }

}
