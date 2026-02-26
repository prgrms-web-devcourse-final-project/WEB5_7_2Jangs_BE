package io.ejangs.docsa.domain.doc.dto.graph;

import java.time.LocalDateTime;

public record BranchGraphDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
                             Long rootCommitId, Long leafCommitId, Long saveId

) {

    public BranchGraphDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
            Long rootCommitId, Long leafCommitId, Long saveId) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt.plusHours(9L);
        this.fromCommitId = fromCommitId;
        this.rootCommitId = rootCommitId;
        this.leafCommitId = leafCommitId;
        this.saveId = saveId;
    }

}
