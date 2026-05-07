package io.ejangs.docsa.domain.doc.readmodel.dto.payload;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;

public record DocThumbnailChangedPayload(
        Long docId,
        String thumbnailObjectKey,
        ThumbnailStatus thumbnailStatus
) {
}
