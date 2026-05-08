package io.ejangs.docsa.domain.doc.thumbnail.app;

import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailResponse;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailSyncResponse;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.image.app.ImageQueryService;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.outbox.s3.app.S3DeleteJobEnqueuer;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.ImageErrorCode;
import io.ejangs.docsa.global.exception.errorcode.ThumbnailErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final ThumbnailQueryService thumbnailQueryService;
    private final DocQueryService docQueryService;
    private final ImageQueryService imageQueryService;
    private final S3DeleteJobEnqueuer s3DeleteJobEnqueuer;

    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    @Value("${cloud.aws.s3.public-base-url}")
    private String cdnUrl;

    @Transactional
    public ThumbnailSyncResponse requestUpdate(Long userId, Long docId) {
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);

        Thumbnail thumbnail = thumbnailQueryService.getOrCreateByDocForUpdate(doc);

        Long requestToken = thumbnail.requestUpdate();

        return new ThumbnailSyncResponse(
                requestToken,
                thumbnail.getSignature(),
                thumbnail.getStatus()
        );
    }

    @Transactional
    public ThumbnailResponse finalizeThumbnail(
            Long userId,
            Long docId,
            Long imageId,
            Long requestToken,
            String signature
    ) {
        docQueryService.checkByIdAndUserId(docId, userId);

        Thumbnail thumbnail = thumbnailQueryService.getByDocIdForUpdate(docId);

        if (!thumbnail.isCurrentToken(requestToken)) {
            throw new CustomException(ThumbnailErrorCode.STALE_THUMBNAIL_REQUEST);
        }

        Image image = imageQueryService.getByIdAndUserId(imageId, userId);

        validateThumbnailImage(docId, image);

        Image previousImage = thumbnail.getCurrentImage();
        thumbnail.complete(image, signature);

        enqueuePreviousThumbnailDeletion(previousImage, image);
        domainEventOutboxPublisher.publish(DomainEventType.DOC_THUMBNAIL_CHANGED, AggregateType.DOC, docId,
                DocPayloadFactory.thumbnailChanged(docId, image.getObjectKey(), ThumbnailStatus.READY));

        return new ThumbnailResponse(
                image.getId(),
                "%s/%s".formatted(cdnUrl, image.getObjectKey()),
                thumbnail.getStatus(),
                thumbnail.getSignature()
        );
    }

    private void validateThumbnailImage(Long docId, Image image) {
        if (!image.getDocId().equals(docId)) {
            throw new CustomException(ThumbnailErrorCode.THUMBNAIL_NOT_FOUND);
        }

        if (image.getStatus() != Image.ImageStatus.ACTIVE) {
            throw new CustomException(ImageErrorCode.IMAGE_UPLOAD_NOT_COMPLETED);
        }

        if (image.getPurpose() != Image.Purpose.DOC_THUMBNAIL) {
            throw new CustomException(ThumbnailErrorCode.INVALID_THUMBNAIL_PURPOSE);
        }
    }

    private void enqueuePreviousThumbnailDeletion(Image previousImage, Image currentImage) {
        if (previousImage == null || previousImage.getId().equals(currentImage.getId())) {
            return;
        }

        s3DeleteJobEnqueuer.enqueueImageDeletion(previousImage);
    }
}
