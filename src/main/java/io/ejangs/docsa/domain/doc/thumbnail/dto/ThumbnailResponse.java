package io.ejangs.docsa.domain.doc.thumbnail.dto;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문서 대표 썸네일 응답")
public record ThumbnailResponse(
        @Schema(description = "대표 썸네일 이미지 id", example = "10")
        Long imageId,
        @Schema(description = "대표 썸네일 CDN URL", example = "https://cdn.example.com/users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.webp")
        String thumbnailUrl,
        @Schema(description = "썸네일 상태(EMPTY/PENDING/READY/FAILED)", example = "READY")
        ThumbnailStatus status,
        @Schema(description = "대표 썸네일이 반영한 프론트 상단 영역 signature", example = "9f0c1dd5d7e8b8f2a1f4c3d2e6a7b8c9")
        String signature
) {

}
