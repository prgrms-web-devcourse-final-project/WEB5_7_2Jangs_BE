package io.ejangs.docsa.domain.image.app;

import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.image.dao.ImageRepository;
import io.ejangs.docsa.domain.image.dto.request.ImageUploadUrlRequest;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadCompleteResponse;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadUrlResponse;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.ImageErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private final ImageRepository imageRepository;
    private final DocQueryService docQueryService;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.presigned-expire-minutes}")
    private long expireMinutes;

    @Value("${cloud.aws.s3.public-base-url}")
    private String cdnUrl;

    private final List<String> contentTypeWhiteList = List.of("image/jpeg", "image/png",
            "image/webp", "image/gif");

    @Transactional
    public ImageUploadUrlResponse createUploadUrl(Long userId, ImageUploadUrlRequest request) {
        docQueryService.getByIdAndUserId(request.docId(), userId);

        validateImage(request.contentType(), request.size());

        String extension = extensionOf(request.contentType());
        String objectKey = "users/%d/docs/%d/images/%s.%s"
                .formatted(userId, request.docId(), UUID.randomUUID(), extension);

        Image image = imageRepository.save(Image.builder()
                .userId(userId)
                .docId(request.docId())
                .originalFileName(request.originalFileName())
                .objectKey(objectKey)
                .contentType(request.contentType())
                .size(request.size())
                .build());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(request.contentType())
                .contentLength(request.size())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expireMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest =
                s3Presigner.presignPutObject(presignRequest);

        return new ImageUploadUrlResponse(
                image.getId(),
                objectKey,
                presignedPutObjectRequest.url().toString(),
                "PUT",
                expireMinutes * 60
        );


    }

    @Transactional
    public ImageUploadCompleteResponse complete(Long userId, Long imageId) {
        Image image = imageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));

        String objectKey = image.getObjectKey();

        HeadObjectResponse head = getHeadObjectOrThrow(objectKey);

        if (!image.getContentType().equals(head.contentType())) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_CONTENT_TYPE);
        }

        if (!image.getSize().equals(head.contentLength())) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_SIZE);
        }

        image.activate();

        String imageUrl = "%s/%s"
                .formatted(cdnUrl, objectKey);

        return new ImageUploadCompleteResponse(imageId, objectKey, imageUrl, image.getContentType(),
                image.getSize(), image.getStatus());
    }

    private void validateImage(String contentType, Long size) {
        if (size > MAX_IMAGE_SIZE) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_SIZE);
        }

        if (!contentTypeWhiteList.contains(contentType)) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_CONTENT_TYPE);
        }
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new CustomException(ImageErrorCode.INVALID_IMAGE_CONTENT_TYPE);
        };
    }

    private HeadObjectResponse getHeadObjectOrThrow(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                throw new CustomException(ImageErrorCode.IMAGE_UPLOAD_NOT_COMPLETED);
            }
            throw e;
        }
    }
}
