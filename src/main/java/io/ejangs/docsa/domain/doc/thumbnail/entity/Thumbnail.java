package io.ejangs.docsa.domain.doc.thumbnail.entity;

import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "doc_thumbnails")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Thumbnail extends BaseEntity {

    public enum ThumbnailStatus {
        EMPTY,
        UPDATING,
        READY,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false, unique = true)
    private Doc doc;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_image_id", unique = true)
    private Image currentImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThumbnailStatus status;

    @Column(nullable = false)
    private Long requestToken;

    @Column(length = 255)
    private String signature;

    private LocalDateTime requestedAt;

    private LocalDateTime generatedAt;

    @Column(length = 500)
    private String lastError;

    @Builder
    private Thumbnail(Doc doc) {
        this.doc = doc;
        this.status = ThumbnailStatus.EMPTY;
        this.requestToken = 0L;
    }

    public Long requestUpdate() {
        this.requestToken++;
        this.status = ThumbnailStatus.UPDATING;
        this.requestedAt = LocalDateTime.now();
        this.lastError = null;
        return this.requestToken;
    }

    public boolean isCurrentToken(Long requestToken) {
        return this.requestToken.equals(requestToken);
    }

    public void complete(Image image, String signature) {
        this.currentImage = image;
        this.signature = signature;
        this.status = ThumbnailStatus.READY;
        this.generatedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void fail(String lastError) {
        this.status = ThumbnailStatus.FAILED;
        this.lastError = lastError;
    }
}
