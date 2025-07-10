package io.ejangs.docsa.domain.document.dto;

import java.time.LocalDateTime;

public record DocumentListSimpleResponse(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

}
