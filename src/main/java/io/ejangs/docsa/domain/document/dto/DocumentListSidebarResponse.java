package io.ejangs.docsa.domain.document.dto;

import java.time.LocalDateTime;

public record DocumentListSidebarResponse(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

}
