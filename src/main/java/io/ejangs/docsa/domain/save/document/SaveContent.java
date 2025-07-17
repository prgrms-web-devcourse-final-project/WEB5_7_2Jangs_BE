package io.ejangs.docsa.domain.save.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private List<Map<String, Object>> content;

    @Builder
    private SaveContent(List<Map<String, Object>> content) {
        this.content = content != null ? content : new ArrayList<>();
    }

    public void updateContent(List<Map<String, Object>> content) {
        this.content = content;
    }
}
