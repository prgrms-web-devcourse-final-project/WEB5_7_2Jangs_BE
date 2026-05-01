package io.ejangs.docsa.global.outbox.mongo.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import io.ejangs.docsa.global.outbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class MongoDeleteOutboxIntegrationTest {

    @Autowired
    private MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @Autowired
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @Autowired
    private MongoDeleteOutboxWorker mongoDeleteOutboxWorker;

    @Autowired
    private MongoDeleteOutboxLifecycleService mongoDeleteOutboxLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private MongoDeleteService mongoDeleteService;

    @BeforeEach
    void cleanOutbox() {
        mongoDeleteOutboxRepository.deleteAllInBatch();
        mongoDeleteOutboxRepository.flush();
    }

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

    @Test
    @DisplayName("동일 키 create 중복 호출 시 outbox는 1건만 유지된다")
    void factoryDedupeWithSameKey() {
        String originId = "dup-" + UUID.randomUUID();
        MongoDeleteOutbox first = mongoDeleteOutboxFactory.createDocCreateCompensation(
                originId,
                new MongoIdsDto(List.of("save-" + originId), List.of(), List.of())
        );
        MongoDeleteOutbox second = mongoDeleteOutboxFactory.createDocCreateCompensation(
                originId,
                new MongoIdsDto(List.of("save-" + originId), List.of(), List.of())
        );

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(mongoDeleteOutboxRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PROCESSING timeout 건은 retryCount 증가 없이 복구되어 재처리된다")
    void workerRecoversTimedOutProcessingBeforeDelete() {
        MongoDeleteOutbox outbox = createOpenOutbox();
        mongoDeleteOutboxLifecycleService.claimOpen(outbox.getId());

        MongoDeleteOutbox processing = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        ReflectionTestUtils.setField(processing, "updatedAt", LocalDateTime.now().minusMinutes(10));
        mongoDeleteOutboxRepository.saveAndFlush(processing);
        jdbcTemplate.update(
                "update mongo_delete_outbox set updated_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(10),
                outbox.getId()
        );

        mongoDeleteOutboxWorker.run();

        MongoDeleteOutbox done = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("상태 불일치 호출(claim/done/retry)은 no-op으로 안전하게 무시된다")
    void completeServiceNoOpOnStatusMismatch() {
        MongoDeleteOutbox outbox = createOpenOutbox();

        mongoDeleteOutboxLifecycleService.done(outbox.getId());
        mongoDeleteOutboxLifecycleService.retry(outbox.getId(), "ignored");

        MongoDeleteOutbox open = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(open.getStatus()).isEqualTo(OutboxStatus.OPEN);
        assertThat(open.getRetryCount()).isEqualTo(0);
        assertThat(open.getLastError()).isNull();

        assertThat(mongoDeleteOutboxLifecycleService.claimOpen(outbox.getId())).isNotNull();
        mongoDeleteOutboxLifecycleService.done(outbox.getId());

        assertThat(mongoDeleteOutboxLifecycleService.claimOpen(outbox.getId())).isNull();
        mongoDeleteOutboxLifecycleService.retry(outbox.getId(), "ignored2");

        MongoDeleteOutbox done = mongoDeleteOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(done.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("워커는 1회 실행 시 OPEN 최대 100건만 처리한다")
    void workerProcessesAtMost100OpenRowsPerRun() {
        for (int i = 0; i < 101; i++) {
            String originId = "batch-" + i + "-" + UUID.randomUUID();
            mongoDeleteOutboxFactory.createDocCreateCompensation(
                    originId,
                    new MongoIdsDto(List.of("save-" + originId), List.of(), List.of())
            );
        }

        mongoDeleteOutboxWorker.run();

        List<MongoDeleteOutbox> firstRun = mongoDeleteOutboxRepository.findAll();
        long doneCountAfterFirst = firstRun.stream().filter(o -> o.getStatus() == OutboxStatus.DONE).count();
        long openCountAfterFirst = firstRun.stream().filter(o -> o.getStatus() == OutboxStatus.OPEN).count();
        assertThat(doneCountAfterFirst).isEqualTo(100);
        assertThat(openCountAfterFirst).isEqualTo(1);

        mongoDeleteOutboxWorker.run();

        List<MongoDeleteOutbox> secondRun = mongoDeleteOutboxRepository.findAll();
        long doneCountAfterSecond = secondRun.stream().filter(o -> o.getStatus() == OutboxStatus.DONE).count();
        long openCountAfterSecond = secondRun.stream().filter(o -> o.getStatus() == OutboxStatus.OPEN).count();
        assertThat(doneCountAfterSecond).isEqualTo(101);
        assertThat(openCountAfterSecond).isEqualTo(0);
    }

    private MongoDeleteOutbox createOpenOutbox() {
        String originId = UUID.randomUUID().toString();
        return mongoDeleteOutboxFactory.createDocCreateCompensation(
                originId,
                new MongoIdsDto(List.of("save-" + originId), List.of(), List.of())
        );
    }
}
