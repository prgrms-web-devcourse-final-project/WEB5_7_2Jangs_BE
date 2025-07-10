package io.ejangs.docsa.domain.commit.dto.request;

import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import java.util.List;
import org.hibernate.validator.constraints.Length;

public record CreateCommitRequest(
        @Length(min = 1, max = 20)
        String title,
        @Length(min = 1, max = 30)
        String description,
        Long branchId,
        List<BlockDto> blocks,
        List<String> blockOrders
) {


}