package io.ejangs.docsa.domain.branch.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> toMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            // 추후 global 하게 사용되면 CustomException 사용
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }
}
