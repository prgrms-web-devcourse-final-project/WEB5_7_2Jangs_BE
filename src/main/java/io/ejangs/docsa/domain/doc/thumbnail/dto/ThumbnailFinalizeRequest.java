package io.ejangs.docsa.domain.doc.thumbnail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "문서 대표 썸네일 확정 요청")
public record ThumbnailFinalizeRequest(
        @Schema(description = "대표 썸네일로 확정할 이미지 id", example = "10")
        @NotNull
        Long imageId,

        @Schema(description = "저장 API 응답으로 받은 최신 썸네일 요청 토큰", example = "12")
        @NotNull
        Long requestToken,

        @Schema(description = "프론트가 썸네일 캡처 대상 영역 기준으로 계산한 signature", example = "9f0c1dd5d7e8b8f2a1f4c3d2e6a7b8c9")
        @NotBlank
        String signature
) {

}
