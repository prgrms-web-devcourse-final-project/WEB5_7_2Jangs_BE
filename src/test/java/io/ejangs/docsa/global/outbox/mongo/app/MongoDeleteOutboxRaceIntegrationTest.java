package io.ejangs.docsa.global.outbox.mongo.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoDeleteOutboxRaceIntegrationTest.RaceBarrierConfig.class)
class MongoDeleteOutboxRaceIntegrationTest {

    private record CreateAttempt(MongoDeleteOutbox outbox, Throwable error) {
    }

    @Autowired
    private MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    @Autowired
    private MongoDeleteOutboxRepository mongoDeleteOutboxRepository;

    @AfterEach
    void cleanOutbox() {
        RaceBarrierAspect.clear();
        mongoDeleteOutboxRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 키 동시 생성 경쟁에서 하나만 생성 후 두 요청은 같은 outbox를 반환한다")
    void concurrentCreateRace() throws Exception {
        String originId = "race-" + UUID.randomUUID();
        MongoIdsDto ids = new MongoIdsDto(List.of("save-" + originId), List.of(), List.of());
        RaceBarrierAspect.arm(originId, new CyclicBarrier(2));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<CreateAttempt> task = () -> {
            try {
                MongoDeleteOutbox outbox = mongoDeleteJobEnqueuer.enqueueDocCreateCompensation(
                        originId,
                        ids
                );
                return new CreateAttempt(outbox, null);
            } catch (Throwable t) {
                return new CreateAttempt(null, t);
            }
        };

        try {
            Future<CreateAttempt> first = executor.submit(task);
            Future<CreateAttempt> second = executor.submit(task);

            CreateAttempt firstAttempt = first.get(10, TimeUnit.SECONDS);
            CreateAttempt secondAttempt = second.get(10, TimeUnit.SECONDS);

            long rowCount = mongoDeleteOutboxRepository.findAll().size();

            assertThat(firstAttempt.error()).isNull();
            assertThat(secondAttempt.error()).isNull();
            assertThat(firstAttempt.outbox()).isNotNull();
            assertThat(secondAttempt.outbox()).isNotNull();
            assertThat(firstAttempt.outbox().getId()).isNotNull();
            assertThat(secondAttempt.outbox().getId()).isNotNull();
            assertThat(firstAttempt.outbox().getId()).isEqualTo(secondAttempt.outbox().getId());
            assertThat(rowCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            RaceBarrierAspect.clear();
        }
    }

    @TestConfiguration
    static class RaceBarrierConfig {
        @Bean
        RaceBarrierAspect raceBarrierAspect() {
            return new RaceBarrierAspect();
        }
    }

    @Aspect
    static class RaceBarrierAspect {
        private static volatile String armedOriginId;
        private static volatile CyclicBarrier barrier;

        static void arm(String originId, CyclicBarrier barrier) {
            RaceBarrierAspect.armedOriginId = originId;
            RaceBarrierAspect.barrier = barrier;
        }

        static void clear() {
            RaceBarrierAspect.armedOriginId = null;
            RaceBarrierAspect.barrier = null;
        }

        @Around("execution(* io.ejangs.docsa.global.outbox.mongo.dao.mysql.MongoDeleteOutboxRepository.findByTriggerTypeAndDomainTypeAndOriginId(..))")
        Object awaitAfterEmptyLookup(ProceedingJoinPoint joinPoint) throws Throwable {
            @SuppressWarnings("unchecked")
            Optional<MongoDeleteOutbox> result = (Optional<MongoDeleteOutbox>) joinPoint.proceed();
            String originIdArg = (String) joinPoint.getArgs()[2];
            CyclicBarrier currentBarrier = barrier;
            if (currentBarrier != null && originIdArg.equals(armedOriginId) && result.isEmpty()) {
                currentBarrier.await(5, TimeUnit.SECONDS);
            }
            return result;
        }
    }
}
