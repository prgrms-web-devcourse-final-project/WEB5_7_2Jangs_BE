package io.ejangs.docsa.domain.doc.readmodel.dto;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import java.time.LocalDateTime;

public record DocListPayload(
        Long docId,
        Long userId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long recentSaveId,
        String thumbnailObjectKey,
        ThumbnailStatus thumbnailStatus
) {
}
