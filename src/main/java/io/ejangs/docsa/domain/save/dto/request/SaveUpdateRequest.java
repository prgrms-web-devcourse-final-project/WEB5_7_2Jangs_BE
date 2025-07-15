package io.ejangs.docsa.domain.save.dto.request;

import io.ejangs.docsa.domain.save.dto.SaveBlock;
import java.util.List;

public record SaveUpdateRequest(List<SaveBlock> content) {

}
