package io.ejangs.docsa.global.init;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class TestMongoCleanupUnitTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoDatabase mongoDatabase;

    @Test
    void cleanup_dropDatabase_whenTestDatabase() {
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.getName()).thenReturn("docsa-test");

        new TestMongoCleanup(mongoTemplate).cleanup();

        verify(mongoDatabase).drop();
    }

    @Test
    void cleanup_skipDrop_whenUnsafeDatabase() {
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.getName()).thenReturn("docsa");

        new TestMongoCleanup(mongoTemplate).cleanup();

        verify(mongoDatabase, never()).drop();
    }
}
