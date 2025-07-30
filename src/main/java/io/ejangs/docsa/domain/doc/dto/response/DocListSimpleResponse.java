package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import java.time.LocalDateTime;

public record DocListSimpleResponse(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RecentActivityDto recent
) {

    public DocListSimpleResponse(Long id, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt, RecentActivityDto recent) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt.plusHours(9L);
        this.updatedAt = updatedAt.plusHours(9L);
        this.recent = recent;
    }
}
