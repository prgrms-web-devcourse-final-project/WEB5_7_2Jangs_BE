package io.ejangs.docsa.domain.commit.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CommitDto(Long id, Long branchId, String title, String description,
                        OffsetDateTime createdAt) {
    public CommitDto(Long id, Long branchId, String title, String description,
            LocalDateTime createdAt

    ) {
        this(id, branchId, title, description, createdAt.atOffset(ZoneOffset.ofHours(9)));
    }

}
