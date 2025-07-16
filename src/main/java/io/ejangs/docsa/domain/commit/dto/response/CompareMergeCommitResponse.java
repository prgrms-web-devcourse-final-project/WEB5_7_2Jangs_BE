package io.ejangs.docsa.domain.commit.dto.response;

import java.util.List;
import java.util.Map;

public record CompareMergeCommitResponse(
        List<Map<String, Object>> base,
        List<Map<String, Object>> target
) {

}
