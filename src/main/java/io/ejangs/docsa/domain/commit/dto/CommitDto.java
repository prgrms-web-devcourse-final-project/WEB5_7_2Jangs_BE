package io.ejangs.docsa.domain.commit.dto;

import java.time.LocalDateTime;

public record CommitDto(
        Long id,
        Long branchId,
        String title,
        String description,
        LocalDateTime createdAt
) {}
