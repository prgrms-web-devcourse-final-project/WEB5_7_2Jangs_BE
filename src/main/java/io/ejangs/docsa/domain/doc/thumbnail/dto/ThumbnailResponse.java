package io.ejangs.docsa.domain.doc.thumbnail.dto;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;

public record ThumbnailResponse(
        Long imageId,
        String thumbnailUrl,
        ThumbnailStatus status,
        String signature
) {

}
