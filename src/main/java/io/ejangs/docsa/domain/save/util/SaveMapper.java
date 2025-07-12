package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class SaveMapper {

    public static SaveUpdateResponse toSaveUpdateResponse(Save save) {
        OffsetDateTime updatedAt = toOffsetDateTime(save.getUpdatedAt());

        return new SaveUpdateResponse(updatedAt);
    }

    public static SaveGetResponse toSaveGetResponse(Save save) {
        OffsetDateTime updatedAt = toOffsetDateTime(save.getUpdatedAt());

        return new SaveGetResponse(updatedAt, save.getContent());
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime.atOffset(ZoneOffset.ofHours(9));
    }
}
