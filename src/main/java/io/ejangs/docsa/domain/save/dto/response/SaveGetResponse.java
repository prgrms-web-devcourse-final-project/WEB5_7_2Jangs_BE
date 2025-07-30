package io.ejangs.docsa.domain.save.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SaveGetResponse(LocalDateTime updatedAt, List<Map<String, Object>> content) {

}
