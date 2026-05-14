package io.ejangs.docsa.domain.doc.readmodel.dto.payload;

import java.time.LocalDateTime;

public record DocTitleChangedPayload(
        Long docId,
        String title,
        LocalDateTime updatedAt
) {
}
