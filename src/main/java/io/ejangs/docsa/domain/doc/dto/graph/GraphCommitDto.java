package io.ejangs.docsa.domain.doc.dto.graph;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GraphCommitDto(Long id, Long branchId, String title, String description,
                             OffsetDateTime createdAt) {
    public GraphCommitDto(Long id, Long branchId, String title, String description,
            LocalDateTime createdAt

    ) {
        this(id, branchId, title, description, createdAt.atOffset(ZoneOffset.ofHours(9)));
    }

}
