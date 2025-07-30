package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class SaveMapper {

    public static SaveUpdateResponse toSaveUpdateResponse(LocalDateTime localDateTime) {
        return new SaveUpdateResponse(localDateTime);
    }

    public static SaveGetResponse toSaveGetResponse(LocalDateTime localDateTime,
            List<Map<String, Object>> content) {
        return new SaveGetResponse(localDateTime, content);
    }
}
