package io.ejangs.docsa.domain.commit.document;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "commitBlockSequence")
public class CommitBlockSequence {

    @Id
    private String id;

    private List<String> blockOrders;

    @Builder
    private CommitBlockSequence(List<String> blockOrders) {
        this.blockOrders = blockOrders;
    }
}
