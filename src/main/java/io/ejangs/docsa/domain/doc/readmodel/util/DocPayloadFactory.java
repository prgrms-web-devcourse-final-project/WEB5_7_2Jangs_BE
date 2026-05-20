package io.ejangs.docsa.domain.doc.readmodel.util;

import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocActivityChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocDeletedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocThumbnailChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.save.entity.Save;

public final class DocPayloadFactory {

    private DocPayloadFactory() {
    }

    public static DocCreatedPayload created(Doc doc, Long userId, Long recentSaveId) {
        return new DocCreatedPayload(
                doc.getId(),
                userId,
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                recentSaveId,
                null,
                ThumbnailStatus.EMPTY
        );
    }

    public static DocTitleChangedPayload titleChanged(Doc doc) {
        return new DocTitleChangedPayload(
                doc.getId(),
                doc.getTitle(),
                doc.getUpdatedAt()
        );
    }

    public static DocActivityChangedPayload activityChanged(
            Long docId,
            Save save
    ) {
        return new DocActivityChangedPayload(
                docId,
                save.getId(),
                save.getUpdatedAt()
        );
    }


    public static DocThumbnailChangedPayload thumbnailChanged(
            Long docId,
            String thumbnailObjectKey,
            ThumbnailStatus thumbnailStatus
    ) {
        return new DocThumbnailChangedPayload(
                docId,
                thumbnailObjectKey,
                thumbnailStatus
        );
    }

    public static DocDeletedPayload deleted(Long docId) {
        return new DocDeletedPayload(
                docId
        );
    }
}
