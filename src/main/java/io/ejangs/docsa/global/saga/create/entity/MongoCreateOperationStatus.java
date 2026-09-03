package io.ejangs.docsa.global.saga.create.entity;

public enum MongoCreateOperationStatus {
    PENDING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
