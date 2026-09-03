package io.ejangs.docsa.global.saga.create.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MongoCreatePlanFactoryTest {

    private final MongoCreatePlanFactory planFactory = new MongoCreatePlanFactory();
    private final MongoCreateRequestHasher requestHasher = new MongoCreateRequestHasher();

    @Test
    @DisplayName("SaveContent 생성 전에 유효한 Mongo ObjectId를 하나 확정한다")
    void plansSaveContentId() {
        MongoIdsDto plan = planFactory.singleSaveContent();

        assertThat(plan.saveContentsIds()).hasSize(1).allMatch(ObjectId::isValid);
        assertThat(plan.commitBlockSequenceIds()).isEmpty();
        assertThat(plan.blockIds()).isEmpty();
    }

    @Test
    @DisplayName("커밋 생성 전에 CBS 하나와 신규 블록 수만큼 ObjectId를 확정한다")
    void plansCommitIds() {
        MongoIdsDto plan = planFactory.commit(3);

        assertThat(plan.commitBlockSequenceIds()).hasSize(1).allMatch(ObjectId::isValid);
        assertThat(plan.blockIds()).hasSize(3).allMatch(ObjectId::isValid);
        assertThat(plan.saveContentsIds()).isEmpty();
    }

    @Test
    @DisplayName("요청 Map의 키 순서가 달라도 같은 요청 hash를 만든다")
    void hashesCanonicalRequest() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("type", "paragraph");
        first.put("id", "editor-1");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("id", "editor-1");
        second.put("type", "paragraph");

        assertThat(requestHasher.hash(first)).isEqualTo(requestHasher.hash(second));
        assertThat(requestHasher.hash(first)).hasSize(64);
    }
}
