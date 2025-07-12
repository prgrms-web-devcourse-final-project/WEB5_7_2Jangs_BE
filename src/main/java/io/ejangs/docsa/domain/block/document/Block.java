package io.ejangs.docsa.domain.block.document;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document
public class Block {

    @Id
    private String id;

    private Map<String, Object> content;

    @Builder
    private Block(Map<String, Object> content) {
        this.content = content;
    }
}
