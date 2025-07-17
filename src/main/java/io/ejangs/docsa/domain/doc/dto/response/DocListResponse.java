package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DocListResponse(
        Long id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String preview,
        RecentActivityDto recent
) {

    public DocListResponse(Long id, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt, String preview, RecentActivityDto recent) {
        this(id, title, createdAt.atOffset(ZoneOffset.ofHours(9)),
                updatedAt.atOffset(ZoneOffset.ofHours(9)), preview, recent);
    }
}
