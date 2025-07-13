package io.ejangs.docsa.domain.commit.util;

import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import java.util.List;

public class CommitBlockSequenceMapper {

    public static CommitBlockSequence toEntity(List<String> blockOrders) {
        return CommitBlockSequence.builder()
                .blockOrders(blockOrders)
                .build();
    }
}
