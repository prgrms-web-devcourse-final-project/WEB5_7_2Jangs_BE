package io.ejangs.docsa.domain.save.dto.request;

import java.util.List;
import java.util.Map;

public record SaveUpdateRequest(List<Map<String, Object>> content) {

}
