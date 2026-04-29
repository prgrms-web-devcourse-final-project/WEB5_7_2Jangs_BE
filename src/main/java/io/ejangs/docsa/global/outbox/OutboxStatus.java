package io.ejangs.docsa.global.outbox;

public enum OutboxStatus {
    OPEN,
    PROCESSING,
    DONE,
    FAILED
}
