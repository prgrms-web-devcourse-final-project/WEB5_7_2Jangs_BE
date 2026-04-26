package io.ejangs.docsa.domain.doc.thumbnail.dto;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;

public record ThumbnailSyncResponse(
        Long requestToken,
        String signature,
        ThumbnailStatus status
) {

}
