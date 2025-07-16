package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record DocListSimpleResponse(
        Long id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        RecentActivityDto recent
) {

    public DocListSimpleResponse(Long id, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt, RecentActivityDto recent) {
        this(id, title, createdAt.atOffset(ZoneOffset.ofHours(9)),
                updatedAt.atOffset(ZoneOffset.ofHours(9)), recent);
    }
}
