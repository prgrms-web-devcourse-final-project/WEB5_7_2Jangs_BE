package io.ejangs.docsa.domain.document.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DocumentListSimpleResponse(
        Long id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public DocumentListSimpleResponse(Long id, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(id, title, createdAt.atOffset(ZoneOffset.ofHours(9)),
                updatedAt.atOffset(ZoneOffset.ofHours(9)));
    }
}
