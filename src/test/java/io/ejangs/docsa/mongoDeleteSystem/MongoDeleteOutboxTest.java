package io.ejangs.docsa.mongoDeleteSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteOutboxWorker;
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteService;
import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OutboxStatus;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutboxFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class MongoDeleteOutboxTest {

    @Autowired
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @Autowired
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Autowired
    private MongoDeleteOutboxWorker mongoDeleteOutboxWorker;

    @MockitoSpyBean
    private MongoDeleteService mongoDeleteService;

    @Test
    @DisplayName("Outbox 워커가 OPEN 건을 처리하면 DONE으로 완료된다")
    void workerDone() {
        MongoDeleteOutbox outbox = createOpenOutbox();

        mongoDeleteOutboxWorker.run();

        MongoDeleteOutbox done = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(0);
        assertThat(done.getDoneAt()).isNotNull();
    }

    @Test
    @DisplayName("Outbox 처리 중 예외가 발생하면 retryCount 증가 후 OPEN 상태로 복귀한다")
    void workerRetryToOpen() {
        MongoDeleteOutbox outbox = createOpenOutbox();
        doThrow(new RuntimeException("mongo delete fail"))
                .when(mongoDeleteService).deleteTarget(any());

        mongoDeleteOutboxWorker.run();

        MongoDeleteOutbox retried = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.OPEN);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getLastError()).contains("mongo delete fail");
    }

    @Test
    @DisplayName("예외가 maxRetry(10회) 누적되면 FAILED 상태가 된다")
    void workerFailedAfterMaxRetry() {
        MongoDeleteOutbox outbox = createOpenOutbox();
        doThrow(new RuntimeException("always fail"))
                .when(mongoDeleteService).deleteTarget(any());

        for (int i = 0; i < 10; i++) {
            mongoDeleteOutboxWorker.run();
        }

        MongoDeleteOutbox failed = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(10);
        assertThat(failed.getLastError()).contains("always fail");
    }

    private MongoDeleteOutbox createOpenOutbox() {
        String originId = UUID.randomUUID().toString();
        return mongoDeleteOutboxFactory.create(
                TriggerType.COMPENSATE,
                DomainType.DOC,
                OriginType.DOC_ID,
                originId,
                new MongoIdsDto(List.of("save-" + originId), List.of(), List.of())
        );
    }
}
