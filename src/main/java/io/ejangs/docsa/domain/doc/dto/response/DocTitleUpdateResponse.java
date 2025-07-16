package io.ejangs.docsa.domain.doc.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DocTitleUpdateResponse(
        Long id,
        String title,
        OffsetDateTime updatedAt
) {

    public DocTitleUpdateResponse(
            Long id,
            String title,
            LocalDateTime updatedAt
    ) {
        this(id, title, updatedAt.atOffset(ZoneOffset.ofHours(9)));
    }
}
