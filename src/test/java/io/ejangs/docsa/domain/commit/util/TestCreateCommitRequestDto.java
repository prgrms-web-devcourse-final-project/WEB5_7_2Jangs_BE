package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import java.util.List;

public record TestCreateCommitRequestDto(
        List<BlockDto> blocks,
        List<String> blockOrders
) {

}
