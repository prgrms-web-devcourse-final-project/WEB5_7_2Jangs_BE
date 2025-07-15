package io.ejangs.docsa.domain.branch.dto;

import java.time.LocalDateTime;

public record BranchDto (
        Long id,
        String name,
        LocalDateTime createdAt,
        Long fromCommitId,
        Long rootCommitId,
        Long leafCommitId,
        Long saveId

){

}
