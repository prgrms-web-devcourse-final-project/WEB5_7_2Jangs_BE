package io.ejangs.docsa.domain.save.dto.response;

import java.time.LocalDateTime;

public record SaveUpdateResponse(LocalDateTime updatedAt) {

    public SaveUpdateResponse(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt.plusHours(9L);
    }
}
