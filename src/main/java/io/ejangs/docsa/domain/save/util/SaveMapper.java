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
        LocalDateTime localDateTime = save.getUpdatedAt();
        ZoneOffset offset = ZoneOffset.ofHours(9); // 예: KST 기준

        OffsetDateTime updatedAt = localDateTime.atOffset(offset);

        return new SaveUpdateResponse(updatedAt);
    }

    public static SaveGetResponse toSaveGetResponse(LocalDateTime localDateTime, List<SaveBlock> content) {
        ZoneOffset offset = ZoneOffset.ofHours(9);
        OffsetDateTime updatedAt = localDateTime.atOffset(offset);

        return new SaveGetResponse(updatedAt, content);
    }
}
