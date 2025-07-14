package io.ejangs.docsa.domain.save.dto;

import java.util.Map;

public record SaveBlock(String id, String type, Map<String, Object> data) {

}
