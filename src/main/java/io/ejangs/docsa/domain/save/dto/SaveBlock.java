package io.ejangs.docsa.domain.save.dto;

import java.util.Map;

public record SaveBlock(Map<String, Object> data) {

    public static SaveBlock from(Map<String, Object> data) {
        return new SaveBlock(data);
    }
}
