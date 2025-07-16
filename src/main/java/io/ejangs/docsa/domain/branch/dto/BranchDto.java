package io.ejangs.docsa.domain.branch.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record BranchDto(Long id, String name, OffsetDateTime createdAt, Long fromCommitId,
                        Long rootCommitId, Long leafCommitId, Long saveId

) {
    public BranchDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
            Long rootCommitId, Long leafCommitId, Long saveId) {
        this(id, name, createdAt.atOffset(ZoneOffset.ofHours(9)), fromCommitId, rootCommitId,
                leafCommitId, saveId);

    }

}
