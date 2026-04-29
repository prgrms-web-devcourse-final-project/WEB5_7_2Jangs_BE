package io.ejangs.docsa.global.outbox.s3.app;

import io.ejangs.docsa.global.outbox.s3.dto.S3DeleteTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class S3DeleteService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public void deleteTarget(S3DeleteTarget target) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(target.objectKey())
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return;
            }
            throw e;
        }
    }
}
