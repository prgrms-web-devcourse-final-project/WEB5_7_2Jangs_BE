package io.ejangs.docsa.global.outbox.s3.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.global.outbox.s3.dto.S3DeleteTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3DeleteExecutorUnitTest {

    @Mock
    private S3Client s3Client;

    private S3DeleteExecutor s3DeleteExecutor;

    @BeforeEach
    void setUp() {
        s3DeleteExecutor = new S3DeleteExecutor(s3Client);
        ReflectionTestUtils.setField(s3DeleteExecutor, "bucket", "docsa-image-bucket");
    }

    @Test
    @DisplayName("S3 삭제 대상 objectKey로 DeleteObject를 호출한다")
    void deleteTarget_success() {
        S3DeleteTarget target = new S3DeleteTarget(10L, "users/1/docs/2/images/old.webp");
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        s3DeleteExecutor.deleteTarget(target);

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        DeleteObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("docsa-image-bucket");
        assertThat(request.key()).isEqualTo(target.objectKey());
    }

    @Test
    @DisplayName("이미 삭제된 S3 객체는 성공으로 처리한다")
    void deleteTarget_success_whenObjectMissing() {
        S3DeleteTarget target = new S3DeleteTarget(10L, "users/1/docs/2/images/missing.webp");
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        s3DeleteExecutor.deleteTarget(target);
    }

    @Test
    @DisplayName("S3 삭제 중 404가 아닌 예외는 재시도를 위해 전파한다")
    void deleteTarget_fail_whenS3ErrorOccurs() {
        S3DeleteTarget target = new S3DeleteTarget(10L, "users/1/docs/2/images/old.webp");
        RuntimeException s3Exception = S3Exception.builder().statusCode(500).build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Exception);

        assertThatThrownBy(() -> s3DeleteExecutor.deleteTarget(target))
                .isSameAs(s3Exception);
    }
}
