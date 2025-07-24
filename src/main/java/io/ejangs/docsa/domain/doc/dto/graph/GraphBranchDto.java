package io.ejangs.docsa.domain.doc.dto.graph;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GraphBranchDto(Long id, String name, OffsetDateTime createdAt, Long fromCommitId,
                             Long rootCommitId, Long leafCommitId, Long saveId

) {
    public GraphBranchDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
            Long rootCommitId, Long leafCommitId, Long saveId) {
        this(id, name, createdAt.atOffset(ZoneOffset.ofHours(9)), fromCommitId, rootCommitId,
                leafCommitId, saveId);

    }

}
