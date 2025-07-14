package io.ejangs.docsa.domain.save.document;

import io.ejangs.docsa.domain.save.dto.SaveBlock;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "saveContent")
public class SaveContent {

    @Id
    private String id;

    private List<SaveBlock> content;

    @Builder
    private SaveContent(List<SaveBlock> content) {
        this.content = content;
    }
}
