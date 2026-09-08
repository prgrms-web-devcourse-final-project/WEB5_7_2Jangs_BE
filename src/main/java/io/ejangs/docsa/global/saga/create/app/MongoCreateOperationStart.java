package io.ejangs.docsa.global.saga.create.app;

public record MongoCreateOperationStart(
        boolean started,
        Long resultEntityId,
        Long resultSaveId
) {

    public static MongoCreateOperationStart newOperation() {
        return new MongoCreateOperationStart(true, null, null);
    }

    public static MongoCreateOperationStart completed(Long resultEntityId, Long resultSaveId) {
        return new MongoCreateOperationStart(false, resultEntityId, resultSaveId);
    }
}
