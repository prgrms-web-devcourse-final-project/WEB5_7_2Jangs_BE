package io.ejangs.docsa.domain.save.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SaveGetResponse(OffsetDateTime updatedAt, List<Map<String, Object>> content) {

}
