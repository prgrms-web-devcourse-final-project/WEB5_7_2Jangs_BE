package io.ejangs.docsa.domain.image.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이미지 업로드 URL 발급 응답")
public record ImageUploadUrlResponse(
        @Schema(description = "생성된 이미지 메타데이터 id", example = "10")
        Long imageId,
        @Schema(description = "S3 object key", example = "users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.png")
        String objectKey,
        @Schema(description = "클라이언트가 PUT 요청을 보낼 presigned URL")
        String uploadUrl,
        @Schema(description = "업로드에 사용할 HTTP method", example = "PUT")
        String method,
        @Schema(description = "URL 만료 시간(초)", example = "300")
        Long expiresInSeconds
) {

}
