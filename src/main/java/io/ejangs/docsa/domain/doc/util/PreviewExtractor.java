package io.ejangs.docsa.domain.doc.util;

import java.util.List;
import java.util.Map;

public class PreviewExtractor {

    public static String doExtractPreview(List<Map<String, Object>> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Map<String, Object> block = blocks.get(i);
            if (block == null) {
                continue;
            }
            Object type = block.get("type");
            Object dataObj = block.get("data");

            if (!"paragraph".equals(type) || !(dataObj instanceof Map)) {
                continue;
            }

            Object textObj = ((Map<?, ?>) dataObj).get("text");
            if (textObj instanceof String text && !text.isBlank()) {
                return text.length() > 200 ? text.substring(0, 200) + "..." : text;
            }
        }
        return "미리보기 없음";
    }
}
