package io.ejangs.docsa.domain.commit.dto.response;

import java.util.List;
import java.util.Map;

public record CommitResponse(
        List<Map<String, Object>> content
) {

}
