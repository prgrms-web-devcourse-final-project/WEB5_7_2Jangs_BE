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
import java.time.LocalDateTime;
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

    // saveContent 가 수정될 경우 명시적으로 updatedAt을 갱신해야 한다.
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSaveMongoId(String saveMongoId) {
        this.saveMongoId = saveMongoId;
    }
}
