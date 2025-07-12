package io.ejangs.docsa.domain.save.dto.response;

import java.time.OffsetDateTime;

public record SaveGetResponse(OffsetDateTime updatedAt, String content) {
}
