package io.ejangs.docsa.domain.image.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.image.dao.ImageRepository;
import io.ejangs.docsa.domain.image.dto.request.ImageUploadUrlRequest;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadCompleteResponse;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadUrlResponse;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.domain.image.entity.Image.ImageStatus;
import io.ejangs.docsa.domain.image.entity.Image.Purpose;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.ImageErrorCode;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class ImageServiceUnitTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageReader imageReader;

    @Mock
    private DocReader docReader;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(imageRepository, imageReader, docReader,
                s3Presigner, s3Client);
        ReflectionTestUtils.setField(imageService, "bucket", "docsa-image-bucket");
        ReflectionTestUtils.setField(imageService, "expireMinutes", 5L);
        ReflectionTestUtils.setField(imageService, "cdnUrl", "https://cdn.example.com");
    }

    @Test
    @DisplayName("업로드 URL 생성 시 users prefix로 S3 key를 만들고 presigned PUT URL을 반환한다")
    void createUploadUrl_success() throws Exception {
        Long userId = 1L;
        Long docId = 2L;
        ImageUploadUrlRequest request =
                new ImageUploadUrlRequest(docId, "sample.png", "image/png", 1024L, Purpose.DOC_CONTENT);

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(org.mockito.Mockito.mock(Doc.class));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image image = invocation.getArgument(0);
            ReflectionTestUtils.setField(image, "id", 10L);
            return image;
        });
        when(presignedPutObjectRequest.url())
                .thenReturn(URI.create("https://s3.example.com/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);

        ImageUploadUrlResponse response = imageService.createUploadUrl(userId, request);

        assertThat(response.imageId()).isEqualTo(10L);
        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
        assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/upload");
        assertThat(response.objectKey())
                .startsWith("users/1/docs/2/images/")
                .endsWith(".png");

        ArgumentCaptor<PutObjectPresignRequest> presignCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(presignCaptor.capture());

        PutObjectPresignRequest presignRequest = presignCaptor.getValue();
        PutObjectRequest putObjectRequest = presignRequest.putObjectRequest();
        assertThat(presignRequest.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(putObjectRequest.bucket()).isEqualTo("docsa-image-bucket");
        assertThat(putObjectRequest.key()).isEqualTo(response.objectKey());
        assertThat(putObjectRequest.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("썸네일 업로드 URL 생성 시 thumbnails prefix로 S3 key를 만든다")
    void createUploadUrl_success_whenPurposeIsThumbnail() throws Exception {
        Long userId = 1L;
        Long docId = 2L;
        ImageUploadUrlRequest request =
                new ImageUploadUrlRequest(docId, "thumbnail.webp", "image/webp", 1024L, Purpose.DOC_THUMBNAIL);

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(org.mockito.Mockito.mock(Doc.class));
        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image image = invocation.getArgument(0);
            ReflectionTestUtils.setField(image, "id", 10L);
            return image;
        });
        when(presignedPutObjectRequest.url())
                .thenReturn(URI.create("https://s3.example.com/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);

        ImageUploadUrlResponse response = imageService.createUploadUrl(userId, request);

        assertThat(response.objectKey())
                .startsWith("users/1/docs/2/thumbnails/")
                .endsWith(".webp");

        ArgumentCaptor<PutObjectPresignRequest> presignCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(presignCaptor.capture());

        PutObjectRequest putObjectRequest = presignCaptor.getValue().putObjectRequest();
        assertThat(putObjectRequest.key()).isEqualTo(response.objectKey());
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("지원하지 않는 이미지 형식이면 업로드 URL을 생성하지 않는다")
    void createUploadUrl_fail_whenContentTypeInvalid() {
        Long userId = 1L;
        Long docId = 2L;
        ImageUploadUrlRequest request =
                new ImageUploadUrlRequest(docId, "sample.svg", "image/svg+xml", 1024L, Purpose.DOC_CONTENT);

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(org.mockito.Mockito.mock(Doc.class));

        assertThatThrownBy(() -> imageService.createUploadUrl(userId, request))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_CONTENT_TYPE));

        verify(imageRepository, never()).save(any(Image.class));
        verifyNoInteractions(s3Presigner);
    }

    @Test
    @DisplayName("S3 객체 확인에 성공하면 이미지를 활성화하고 CDN URL을 반환한다")
    void complete_success() {
        Long userId = 1L;
        Long imageId = 10L;
        String objectKey = "users/1/docs/2/images/image.png";
        Image image = Image.builder()
                .userId(userId)
                .docId(2L)
                .originalFileName("sample.png")
                .objectKey(objectKey)
                .contentType("image/png")
                .size(1024L)
                .purpose(Purpose.DOC_CONTENT)
                .build();

        when(imageReader.getByIdAndUserId(imageId, userId)).thenReturn(image);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(1024L)
                        .build());

        ImageUploadCompleteResponse response = imageService.complete(userId, imageId);

        assertThat(image.getStatus()).isEqualTo(ImageStatus.ACTIVE);
        assertThat(response.imageId()).isEqualTo(imageId);
        assertThat(response.objectKey()).isEqualTo(objectKey);
        assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/" + objectKey);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.size()).isEqualTo(1024L);
        assertThat(response.status()).isEqualTo(ImageStatus.ACTIVE);

        ArgumentCaptor<HeadObjectRequest> headObjectCaptor =
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(headObjectCaptor.capture());
        assertThat(headObjectCaptor.getValue().bucket()).isEqualTo("docsa-image-bucket");
        assertThat(headObjectCaptor.getValue().key()).isEqualTo(objectKey);
    }

    @Test
    @DisplayName("S3 객체가 없거나 접근할 수 없으면 업로드 미완료 예외로 변환한다")
    void complete_fail_whenUploadNotCompleted() {
        Long userId = 1L;
        Long imageId = 10L;
        Image image = Image.builder()
                .userId(userId)
                .docId(2L)
                .originalFileName("sample.png")
                .objectKey("users/1/docs/2/images/image.png")
                .contentType("image/png")
                .size(1024L)
                .purpose(Purpose.DOC_CONTENT)
                .build();

        when(imageReader.getByIdAndUserId(imageId, userId)).thenReturn(image);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(404)
                        .message("NoSuchKey")
                        .build());

        assertThatThrownBy(() -> imageService.complete(userId, imageId))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ImageErrorCode.IMAGE_UPLOAD_NOT_COMPLETED));

        assertThat(image.getStatus()).isEqualTo(ImageStatus.PENDING);
    }
}
