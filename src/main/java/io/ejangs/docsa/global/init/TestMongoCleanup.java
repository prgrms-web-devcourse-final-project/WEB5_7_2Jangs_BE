package io.ejangs.docsa.global.init;

import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "mongo.test-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class TestMongoCleanup {

    private final MongoTemplate mongoTemplate;

    @PreDestroy
    public void cleanup() {
        MongoDatabase database = mongoTemplate.getDb();
        String databaseName = database.getName();
        if (!isSafeDatabaseName(databaseName)) {
            log.warn("[TestMongoCleanup] Skip cleanup. Unsafe databaseName={}", databaseName);
            return;
        }
        database.drop();
        log.info("[TestMongoCleanup] Dropped MongoDB database: {}", databaseName);
    }

    private boolean isSafeDatabaseName(String databaseName) {
        return databaseName != null && databaseName.endsWith("-test");
    }
}
