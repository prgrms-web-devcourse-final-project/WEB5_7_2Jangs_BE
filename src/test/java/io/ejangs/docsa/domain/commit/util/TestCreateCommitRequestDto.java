package io.ejangs.docsa.domain.commit.util;

import java.util.List;
import java.util.Map;

public record TestCreateCommitRequestDto(
        List<Map<String, Object>> blocks,
        List<String> blockOrders
) {

}
