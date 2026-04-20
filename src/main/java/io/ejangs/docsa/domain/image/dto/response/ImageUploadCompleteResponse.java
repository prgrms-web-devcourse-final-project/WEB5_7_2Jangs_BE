package io.ejangs.docsa.domain.image.dto.response;

import io.ejangs.docsa.domain.image.entity.Image.ImageStatus;

public record ImageUploadCompleteResponse(
        Long imageId,
        String objectKey,
        String imageUrl,
        String contentType,
        Long size,
        ImageStatus status
) {
}
