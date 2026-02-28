package io.ejangs.docsa.domain.edge.dto.graph;

import java.time.LocalDateTime;

public record CommitGraphDto(Long id, Long branchId, String title, String description,
                             LocalDateTime createdAt) {

    public CommitGraphDto(Long id, Long branchId, String title, String description,
            LocalDateTime createdAt) {
        this.id = id;
        this.branchId = branchId;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt.plusHours(9L);
    }
}
