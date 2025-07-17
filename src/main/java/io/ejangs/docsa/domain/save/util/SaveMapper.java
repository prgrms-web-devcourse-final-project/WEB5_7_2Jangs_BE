package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class SaveMapper {

    public static SaveUpdateResponse toSaveUpdateResponse(Save save) {
        return new SaveUpdateResponse(toOffsetDateTime(save.getUpdatedAt()));
    }

    public static SaveGetResponse toSaveGetResponse(LocalDateTime localDateTime, List<SaveBlock> content) {
        return new SaveGetResponse(toOffsetDateTime(localDateTime), content);
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime.atOffset(ZoneOffset.ofHours(9));
    }
}
