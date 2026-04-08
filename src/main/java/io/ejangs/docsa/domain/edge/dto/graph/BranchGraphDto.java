package io.ejangs.docsa.domain.edge.dto.graph;

import java.time.LocalDateTime;

public record BranchGraphDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
                             Long mergeTargetCommitId, Long rootCommitId, Long leafCommitId,
                             Long saveId

) {

    public BranchGraphDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
            Long mergeTargetCommitId, Long rootCommitId, Long leafCommitId, Long saveId) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt.plusHours(9L);
        this.fromCommitId = fromCommitId;
        this.mergeTargetCommitId = mergeTargetCommitId;
        this.rootCommitId = rootCommitId;
        this.leafCommitId = leafCommitId;
        this.saveId = saveId;
    }

}
