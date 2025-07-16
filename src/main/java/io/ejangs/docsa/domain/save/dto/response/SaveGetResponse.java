package io.ejangs.docsa.domain.save.dto.response;

import io.ejangs.docsa.domain.save.dto.SaveBlock;
import java.time.OffsetDateTime;
import java.util.List;

public record SaveGetResponse(OffsetDateTime updatedAt, List<SaveBlock> content) {

}
