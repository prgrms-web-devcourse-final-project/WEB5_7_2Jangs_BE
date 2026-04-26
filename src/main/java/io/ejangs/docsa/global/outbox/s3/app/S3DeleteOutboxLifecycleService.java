package io.ejangs.docsa.global.outbox.s3.app;

import io.ejangs.docsa.domain.image.dao.ImageRepository;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import io.ejangs.docsa.global.outbox.s3.dao.S3DeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.s3.dto.S3DeleteTarget;
import io.ejangs.docsa.global.outbox.s3.entity.S3DeleteOutbox;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class S3DeleteOutboxLifecycleService {

    private final S3DeleteOutboxRepository s3DeleteOutboxRepository;
    private final ImageRepository imageRepository;

    public S3DeleteTarget claimOpen(Long outboxId) {
        S3DeleteOutbox targetOutbox = s3DeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.OPEN)
                .orElse(null);
        if (targetOutbox == null) {
            return null;
        }

        targetOutbox.markProcessing();
        s3DeleteOutboxRepository.save(targetOutbox);
        return new S3DeleteTarget(targetOutbox.getImageId(), targetOutbox.getObjectKey());
    }

    public void done(Long outboxId) {
        S3DeleteOutbox targetOutbox = s3DeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markDone();
        s3DeleteOutboxRepository.save(targetOutbox);
        imageRepository.findById(targetOutbox.getImageId())
                .ifPresent(image -> {
                    image.markDeleted();
                    imageRepository.save(image);
                });
    }

    public void retry(Long outboxId, String errorMessage) {
        S3DeleteOutbox targetOutbox = s3DeleteOutboxRepository
                .findByIdAndStatus(outboxId, OutboxStatus.PROCESSING)
                .orElse(null);
        if (targetOutbox == null) {
            return;
        }

        targetOutbox.markRetry(errorMessage);
        s3DeleteOutboxRepository.save(targetOutbox);
    }

    public int recoverTimedOutProcessing(LocalDateTime threshold) {
        List<S3DeleteOutbox> stuckOutboxes = s3DeleteOutboxRepository
                .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        OutboxStatus.PROCESSING,
                        threshold
                );
        if (stuckOutboxes.isEmpty()) {
            return 0;
        }

        stuckOutboxes.forEach(outbox -> outbox.markRetry(outbox.getLastError() + " (recovered)"));
        s3DeleteOutboxRepository.saveAll(stuckOutboxes);
        return stuckOutboxes.size();
    }
}
