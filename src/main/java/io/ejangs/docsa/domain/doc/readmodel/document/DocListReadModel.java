package io.ejangs.docsa.domain.doc.readmodel.document;

import io.ejangs.docsa.domain.doc.readmodel.dto.DocListPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("doc_list_read_models")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocListReadModel {

    @Id
    private Long id; // docId

    @Indexed
    private Long userId;

    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long recentSaveId;

    private String thumbnailObjectKey;
    private ThumbnailStatus thumbnailStatus;

    private boolean deleted;

    private Long lastProjectedEventId;

    public static DocListReadModel create(DocListPayload payload, Long eventId) {
        DocListReadModel model = new DocListReadModel();
        model.id = payload.docId();
        model.userId = payload.userId();
        model.title = payload.title();
        model.createdAt = payload.createdAt();
        model.updatedAt = payload.updatedAt();
        model.recentSaveId = payload.recentSaveId();
        model.thumbnailObjectKey = payload.thumbnailObjectKey();
        model.thumbnailStatus = payload.thumbnailStatus();
        model.deleted = false;
        model.lastProjectedEventId = eventId;
        return model;
    }

    public void apply(DocListPayload payload, Long eventId) {
        this.title = payload.title();
        this.updatedAt = payload.updatedAt();
        this.recentSaveId = payload.recentSaveId();
        this.thumbnailObjectKey = payload.thumbnailObjectKey();
        this.thumbnailStatus = payload.thumbnailStatus();
        this.deleted = false;
        this.lastProjectedEventId = eventId;
    }

    public void markDeleted(Long eventId) {
        this.deleted = true;
        this.lastProjectedEventId = eventId;
    }
}
