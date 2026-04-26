package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import java.time.LocalDateTime;

public record DocPageResponse(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String thumbnailUrl,
        ThumbnailStatus thumbnailStatus,
        RecentActivityDto recent
) {

    public DocPageResponse {
        createdAt = createdAt.plusHours(9L);
        updatedAt = updatedAt.plusHours(9L);
    }
}
