package io.ejangs.docsa.domain.image.dto.response;

import io.ejangs.docsa.domain.image.entity.Image.ImageStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이미지 업로드 완료 응답")
public record ImageUploadCompleteResponse(
        @Schema(description = "이미지 id", example = "10")
        Long imageId,
        @Schema(description = "S3 object key", example = "users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.png")
        String objectKey,
        @Schema(description = "CloudFront 또는 CDN 이미지 URL")
        String imageUrl,
        @Schema(description = "검증된 이미지 Content-Type", example = "image/png")
        String contentType,
        @Schema(description = "검증된 파일 크기(byte)", example = "102400")
        Long size,
        @Schema(description = "이미지 상태", example = "ACTIVE")
        ImageStatus status
) {
}
