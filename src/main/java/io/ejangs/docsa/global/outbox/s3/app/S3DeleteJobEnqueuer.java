package io.ejangs.docsa.global.outbox.s3.app;

import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.global.outbox.s3.dao.S3DeleteOutboxRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3DeleteJobEnqueuer {

    private final S3DeleteOutboxRepository s3DeleteOutboxRepository;

    public void enqueueImageDeletion(Image image) {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(image.getId(), "imageId is required");
        Objects.requireNonNull(image.getObjectKey(), "objectKey is required");

        image.markDeleting();
        s3DeleteOutboxRepository.insertOpenIfAbsent(image.getId(), image.getObjectKey());
    }
}
