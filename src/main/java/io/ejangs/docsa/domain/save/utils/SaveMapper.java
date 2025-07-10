package io.ejangs.docsa.domain.save.utils;

import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;

public class SaveMapper {

    public static SaveUpdateResponse toSaveUpdateResponse(Save save) {
        return new SaveUpdateResponse(save.getUpdatedAt());
    }
}
