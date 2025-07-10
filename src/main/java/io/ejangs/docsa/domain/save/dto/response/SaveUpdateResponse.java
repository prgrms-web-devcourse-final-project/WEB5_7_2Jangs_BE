package io.ejangs.docsa.domain.save.dto.response;

import java.time.LocalDateTime;

public record SaveUpdateResponse(LocalDateTime updatedAt) {

    public static SaveUpdateResponse from(LocalDateTime updatedAt) {
        return new SaveUpdateResponse(updatedAt);
    }
}
