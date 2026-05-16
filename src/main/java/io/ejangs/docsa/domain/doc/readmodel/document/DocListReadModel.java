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
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("doc_list_read_models")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_doc_list_user_deleted_updated",
                def = "{'userId': 1, 'deleted': 1, 'updatedAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_doc_list_user_deleted_title",
                def = "{'userId': 1, 'deleted': 1, 'title': 1}"
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocListReadModel {

    @Id
    private Long id; // docId

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

    public static DocListReadModel backfill(
            Long docId,
            Long userId,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long recentSaveId,
            String thumbnailObjectKey,
            ThumbnailStatus thumbnailStatus
    ) {
        DocListReadModel model = new DocListReadModel();
        model.id = docId;
        model.userId = userId;
        model.title = title;
        model.createdAt = createdAt;
        model.updatedAt = updatedAt;
        model.recentSaveId = recentSaveId;
        model.thumbnailObjectKey = thumbnailObjectKey;
        model.thumbnailStatus = thumbnailStatus;
        model.deleted = false;
        model.lastProjectedEventId = null;
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
