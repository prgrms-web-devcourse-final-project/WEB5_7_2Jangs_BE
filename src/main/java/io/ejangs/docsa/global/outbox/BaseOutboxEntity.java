package io.ejangs.docsa.global.outbox;

import io.ejangs.docsa.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseOutboxEntity extends BaseEntity {

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    protected OutboxStatus status;

    @Column(nullable = false)
    protected Integer retryCount;

    @Column(nullable = false)
    protected Integer maxRetry;

    protected LocalDateTime doneAt;

    @Column(length = 2000)
    protected String lastError;

    @Version
    protected Long version;

    protected void initOutbox() {
        this.status = OutboxStatus.OPEN;
        this.retryCount = 0;
        this.maxRetry = 10;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        updateTimestamp();
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.doneAt = LocalDateTime.now();
        this.lastError = null;
        updateTimestamp();
    }

    public void markRetry(String errorMessage) {
        this.retryCount = this.retryCount + 1;
        this.lastError = errorMessage;

        if (this.retryCount >= this.maxRetry) {
            this.status = OutboxStatus.FAILED;
            updateTimestamp();
            return;
        }

        this.status = OutboxStatus.OPEN;
        updateTimestamp();
    }
}
