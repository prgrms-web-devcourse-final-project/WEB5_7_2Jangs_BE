package io.ejangs.docsa.domain.save.dto.response;

import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailSyncResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "저장 수정 응답")
public record SaveUpdateResponse(
        @Schema(description = "저장 수정 시각", example = "2026-04-26T21:30:00")
        LocalDateTime updatedAt,
        @Schema(description = "프론트 썸네일 동기화 판단에 필요한 정보")
        ThumbnailSyncResponse thumbnail
) {

    public SaveUpdateResponse(LocalDateTime updatedAt, ThumbnailSyncResponse thumbnail) {
        this.updatedAt = updatedAt.plusHours(9L);
        this.thumbnail = thumbnail;
    }
}
