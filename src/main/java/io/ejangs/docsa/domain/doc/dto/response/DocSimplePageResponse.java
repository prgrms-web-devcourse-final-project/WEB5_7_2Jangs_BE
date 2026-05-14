package io.ejangs.docsa.domain.doc.dto.response;

import java.time.LocalDateTime;

public record DocSimplePageResponse(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long recentSaveId
) {

    public DocSimplePageResponse(Long id, String title, LocalDateTime createdAt,
            LocalDateTime updatedAt, Long recentSaveId) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt.plusHours(9L);
        this.updatedAt = updatedAt.plusHours(9L);
        this.recentSaveId = recentSaveId;
    }
}
