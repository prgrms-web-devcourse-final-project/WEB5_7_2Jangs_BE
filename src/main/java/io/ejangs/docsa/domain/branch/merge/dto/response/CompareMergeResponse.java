package io.ejangs.docsa.domain.branch.merge.dto.response;

import java.util.List;
import java.util.Map;

public record CompareMergeResponse(
        List<Map<String, Object>> base,
        List<Map<String, Object>> target
) {

}
