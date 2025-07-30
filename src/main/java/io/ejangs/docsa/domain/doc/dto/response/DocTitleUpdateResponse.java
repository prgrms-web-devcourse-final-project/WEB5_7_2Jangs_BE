package io.ejangs.docsa.domain.doc.dto.response;

import java.time.LocalDateTime;

public record DocTitleUpdateResponse(
        Long id,
        String title,
        LocalDateTime updatedAt
) {

    public DocTitleUpdateResponse(
            Long id,
            String title,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt.plusHours(9L);
    }
}
