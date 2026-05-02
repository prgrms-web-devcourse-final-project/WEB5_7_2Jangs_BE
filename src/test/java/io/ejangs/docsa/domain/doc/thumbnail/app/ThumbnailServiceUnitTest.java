package io.ejangs.docsa.domain.doc.thumbnail.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailResponse;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.image.app.ImageQueryService;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.domain.image.entity.Image.ImageStatus;
import io.ejangs.docsa.domain.image.entity.Image.Purpose;
import io.ejangs.docsa.global.outbox.s3.app.S3DeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.s3.dao.S3DeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.s3.entity.S3DeleteOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceUnitTest {

    @Mock
    private ThumbnailQueryService thumbnailQueryService;

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private ImageQueryService imageQueryService;

    @Mock
    private S3DeleteOutboxRepository s3DeleteOutboxRepository;

    private ThumbnailService thumbnailService;

    @BeforeEach
    void setUp() {
        S3DeleteJobEnqueuer s3DeleteJobEnqueuer =
                new S3DeleteJobEnqueuer(s3DeleteOutboxRepository);
        thumbnailService = new ThumbnailService(
                thumbnailQueryService,
                docQueryService,
                imageQueryService,
                s3DeleteJobEnqueuer
        );
        ReflectionTestUtils.setField(thumbnailService, "cdnUrl", "https://cdn.example.com");
    }

    @Test
    @DisplayName("기존 썸네일을 새 이미지로 교체하면 이전 S3 객체 삭제 Outbox를 적재한다")
    void finalizeThumbnail_enqueuePreviousThumbnailDeletion() {
        Long userId = 1L;
        Long docId = 2L;
        Long requestToken = 1L;
        Image oldImage = activeThumbnailImage(10L, userId, docId, "old.webp");
        Image newImage = activeThumbnailImage(11L, userId, docId, "new.webp");
        Thumbnail thumbnail = thumbnailWithCurrentImage(oldImage, requestToken);

        when(thumbnailQueryService.getByDocIdForUpdate(docId)).thenReturn(thumbnail);
        when(imageQueryService.getByIdAndUserId(newImage.getId(), userId)).thenReturn(newImage);

        ThumbnailResponse response = thumbnailService.finalizeThumbnail(
                userId,
                docId,
                newImage.getId(),
                requestToken,
                "new-signature"
        );

        assertThat(response.imageId()).isEqualTo(newImage.getId());
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/" + newImage.getObjectKey());
        assertThat(thumbnail.getCurrentImage()).isEqualTo(newImage);
        assertThat(oldImage.getStatus()).isEqualTo(ImageStatus.DELETING);
        verify(s3DeleteOutboxRepository).insertOpenIfAbsent(
                10L,
                "users/1/docs/2/images/old.webp"
        );
    }

    @Test
    @DisplayName("같은 이미지를 다시 확정하면 삭제 Outbox를 만들지 않는다")
    void finalizeThumbnail_skipDeletionWhenImageIsSame() {
        Long userId = 1L;
        Long docId = 2L;
        Long requestToken = 1L;
        Image image = activeThumbnailImage(10L, userId, docId, "same.webp");
        Thumbnail thumbnail = thumbnailWithCurrentImage(image, requestToken);

        when(thumbnailQueryService.getByDocIdForUpdate(docId)).thenReturn(thumbnail);
        when(imageQueryService.getByIdAndUserId(image.getId(), userId)).thenReturn(image);

        thumbnailService.finalizeThumbnail(
                userId,
                docId,
                image.getId(),
                requestToken,
                "same-signature"
        );

        assertThat(image.getStatus()).isEqualTo(ImageStatus.ACTIVE);
        verify(s3DeleteOutboxRepository, never()).save(any(S3DeleteOutbox.class));
    }

    private Thumbnail thumbnailWithCurrentImage(Image image, Long expectedRequestToken) {
        Thumbnail thumbnail = Thumbnail.builder()
                .doc(mock(Doc.class))
                .build();
        thumbnail.complete(image, "old-signature");
        Long requestToken = thumbnail.requestUpdate();
        assertThat(requestToken).isEqualTo(expectedRequestToken);
        return thumbnail;
    }

    private Image activeThumbnailImage(Long imageId, Long userId, Long docId, String fileName) {
        Image image = Image.builder()
                .userId(userId)
                .docId(docId)
                .originalFileName(fileName)
                .objectKey("users/%d/docs/%d/images/%s".formatted(userId, docId, fileName))
                .contentType("image/webp")
                .size(1024L)
                .purpose(Purpose.DOC_THUMBNAIL)
                .build();
        ReflectionTestUtils.setField(image, "id", imageId);
        image.activate();
        return image;
    }
}
