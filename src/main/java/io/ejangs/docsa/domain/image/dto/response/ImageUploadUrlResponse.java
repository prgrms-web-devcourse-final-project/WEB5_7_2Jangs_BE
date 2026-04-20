package io.ejangs.docsa.domain.image.dto.response;

public record ImageUploadUrlResponse(
        Long imageId,
        String objectKey,
        String uploadUrl,
        String method,
        Long expiresInSeconds
) {

}
