package io.ejangs.docsa.domain.save.dto.response;

import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailSyncResponse;
import java.time.LocalDateTime;

public record SaveUpdateResponse(LocalDateTime updatedAt,
                                 ThumbnailSyncResponse thumbnail) {

    public SaveUpdateResponse(LocalDateTime updatedAt, ThumbnailSyncResponse thumbnail) {
        this.updatedAt = updatedAt.plusHours(9L);
        this.thumbnail = thumbnail;
    }
}
