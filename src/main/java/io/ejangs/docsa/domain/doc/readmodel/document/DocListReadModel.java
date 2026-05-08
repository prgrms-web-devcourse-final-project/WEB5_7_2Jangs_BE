package io.ejangs.docsa.domain.doc.readmodel.document;

import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocActivityChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocThumbnailChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
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

    public static DocListReadModel create(DocCreatedPayload payload, Long eventId) {
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

    public boolean changeTitle(DocTitleChangedPayload payload, Long eventId) {
        if (isAlreadyProjected(eventId)) {
            return false;
        }

        this.title = payload.title();
        this.updatedAt = payload.updatedAt();
        this.deleted = false;
        this.lastProjectedEventId = eventId;
        return true;
    }

    public boolean changeActivity(DocActivityChangedPayload payload, Long eventId) {
        if (isAlreadyProjected(eventId)) {
            return false;
        }

        this.recentSaveId = payload.recentSaveId();
        this.updatedAt = payload.updatedAt();
        this.deleted = false;
        this.lastProjectedEventId = eventId;
        return true;
    }

    public boolean changeThumbnail(DocThumbnailChangedPayload payload, Long eventId) {
        if (isAlreadyProjected(eventId)) {
            return false;
        }

        this.thumbnailObjectKey = payload.thumbnailObjectKey();
        this.thumbnailStatus = payload.thumbnailStatus();
        this.lastProjectedEventId = eventId;
        return true;
    }

    public boolean markDeleted(Long eventId) {
        if (isAlreadyProjected(eventId)) {
            return false;
        }

        this.deleted = true;
        this.lastProjectedEventId = eventId;
        return true;
    }

    private boolean isAlreadyProjected(Long eventId) {
        return this.lastProjectedEventId != null && this.lastProjectedEventId >= eventId;
    }
}
