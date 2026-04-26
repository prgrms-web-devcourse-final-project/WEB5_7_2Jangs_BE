package io.ejangs.docsa.domain.doc.thumbnail.dto;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장 후 썸네일 동기화 정보")
public record ThumbnailSyncResponse(
        @Schema(description = "최신 썸네일 요청 토큰. finalize 시 그대로 전달해야 합니다.", example = "12")
        Long requestToken,
        @Schema(description = "현재 대표 썸네일이 반영한 signature. 프론트가 계산한 signature와 같으면 업로드를 생략합니다.", example = "9f0c1dd5d7e8b8f2a1f4c3d2e6a7b8c9")
        String signature,
        @Schema(description = "썸네일 상태(EMPTY/PENDING/READY/FAILED)", example = "PENDING")
        ThumbnailStatus status
) {

}
