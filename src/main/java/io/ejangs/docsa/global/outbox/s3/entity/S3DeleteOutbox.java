package io.ejangs.docsa.global.outbox.s3.entity;

import io.ejangs.docsa.global.outbox.BaseOutboxEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "s3_delete_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_s3_delete_outbox_object_key", columnNames = "object_key")
        },
        indexes = {
                @Index(name = "idx_s3_delete_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_s3_delete_outbox_status_updated_at", columnList = "status, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class S3DeleteOutbox extends BaseOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long imageId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    public static S3DeleteOutbox open(Long imageId, String objectKey) {
        S3DeleteOutbox outbox = new S3DeleteOutbox();
        outbox.imageId = imageId;
        outbox.objectKey = objectKey;
        outbox.initOutbox();
        return outbox;
    }
}
