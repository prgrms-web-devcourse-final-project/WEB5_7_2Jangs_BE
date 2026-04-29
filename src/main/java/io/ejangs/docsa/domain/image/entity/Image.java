package io.ejangs.docsa.domain.image.entity;

import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image extends BaseEntity {

    public enum ImageStatus {
        PENDING,
        ACTIVE,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    @Builder
    private Image(Long userId, Long docId, String originalFileName, String objectKey, String contentType, Long size) {
        this.userId = userId;
        this.docId = docId;
        this.originalFileName = originalFileName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.size = size;
        this.status = ImageStatus.PENDING;
    }

    public void activate() {
        this.status = ImageStatus.ACTIVE;
    }

    public void fail() {
        this.status = ImageStatus.FAILED;
    }
}
