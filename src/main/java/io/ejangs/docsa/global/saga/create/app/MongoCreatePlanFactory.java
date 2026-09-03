package io.ejangs.docsa.global.saga.create.app;

import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import java.util.List;
import java.util.stream.IntStream;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

@Component
public class MongoCreatePlanFactory {

    public MongoIdsDto singleSaveContent() {
        return new MongoIdsDto(List.of(newObjectId()), List.of(), List.of());
    }

    public MongoIdsDto commit(int newBlockCount) {
        if (newBlockCount < 0) {
            throw new IllegalArgumentException("newBlockCount must not be negative");
        }
        List<String> blockIds = IntStream.range(0, newBlockCount)
                .mapToObj(ignored -> newObjectId())
                .toList();
        return new MongoIdsDto(List.of(), List.of(newObjectId()), blockIds);
    }

    private String newObjectId() {
        return new ObjectId().toHexString();
    }
}
