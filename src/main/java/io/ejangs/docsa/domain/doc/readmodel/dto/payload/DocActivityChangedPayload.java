package io.ejangs.docsa.domain.doc.readmodel.dto.payload;

import java.time.LocalDateTime;

public record DocActivityChangedPayload(
        Long docId,
        Long recentSaveId,
        LocalDateTime updatedAt
) {
}
