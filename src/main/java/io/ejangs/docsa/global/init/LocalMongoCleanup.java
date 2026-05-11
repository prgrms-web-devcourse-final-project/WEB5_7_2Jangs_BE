package io.ejangs.docsa.global.init;

import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "mongo.local-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class LocalMongoCleanup implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropDatabase("startup");
    }

    @PreDestroy
    public void cleanup() {
        dropDatabase("shutdown");
    }

    private void dropDatabase(String phase) {
        MongoDatabase database = mongoTemplate.getDb();
        String databaseName = database.getName();
        if (!isSafeDatabaseName(databaseName)) {
            log.warn("[LocalMongoCleanup] Skip {} cleanup. Unsafe databaseName={}", phase, databaseName);
            return;
        }
        database.drop();
        log.info("[LocalMongoCleanup] Dropped MongoDB database on {}: {}", phase, databaseName);
    }

    private boolean isSafeDatabaseName(String databaseName) {
        return databaseName != null
                && (databaseName.endsWith("-local") || databaseName.endsWith("-test"));
    }
}
