package io.ejangs.docsa.domain.block.dto.response;

import java.util.Map;

public record BlockDto(
        String id,
        String type,
        Map<String, Object> data,
        Map<String, Object> tunes
) {

}