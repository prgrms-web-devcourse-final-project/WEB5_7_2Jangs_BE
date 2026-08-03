package io.ejangs.docsa.domain.commit.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.ejangs.docsa.global.config.CacheConfig;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CommitContentRedisIntegrationTest {

    private static final String BASE_PREFIX = "docsa:experiment:commit-content:";
    private static final String REDIS_STOP_FLAG = "/tmp/docsa-redis-stop";
    private static final Duration IO_TIMEOUT = Duration.ofMillis(200);
    private static final String REDIS_WRAPPER_COMMAND = """
            while true; do
              if [ -e %s ]; then
                sleep 0.1
                continue
              fi
              redis-server --save '' --appendonly no &
              redis_pid=$!
              wait "$redis_pid" || true
              sleep 0.1
            done
            """.formatted(REDIS_STOP_FLAG);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("sh", "-c", REDIS_WRAPPER_COMMAND);

    private final Set<String> keysToDelete = ConcurrentHashMap.newKeySet();

    @AfterEach
    void restoreRedisAndDeleteOnlyCreatedKeys() {
        ensureRedisRunning();
        LettuceConnectionFactory connectionFactory = connectionFactory();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            keysToDelete.stream()
                    .map(key -> key.getBytes(StandardCharsets.UTF_8))
                    .forEach(connection.keyCommands()::del);
        } finally {
            connectionFactory.destroy();
        }
        keysToDelete.clear();
    }

    @Test
    @DisplayName("Stream.toList로 만든 중첩 커밋 본문은 실제 Redis에서 직렬화되어 두 번째 조회에 적중한다")
    void nestedCommitContentRoundTripsThroughRedis() {
        String prefix = uniquePrefix();
        String commitId = "commit-round-trip";
        remember(prefix, commitId);

        runContext(prefix, Duration.ofMinutes(1), context -> {
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            AtomicInteger loadCount = new AtomicInteger();
            List<Map<String, Object>> assembled = assembledContent();

            List<Map<String, Object>> first = cache.get(commitId, () -> {
                loadCount.incrementAndGet();
                return assembled;
            });
            List<Map<String, Object>> second = cache.get(commitId, () -> {
                loadCount.incrementAndGet();
                return List.of();
            });

            assertThat(first).isEqualTo(assembled);
            assertThat(second).isEqualTo(assembled);
            assertThat(loadCount).hasValue(1);
        });
    }

    @Test
    @DisplayName("커밋 캐시 키는 설정한 namespace에만 생성되고 인증 캐시와 분리된다")
    void commitKeyUsesConfiguredNamespaceAndIsolatedManager() {
        String prefix = uniquePrefix();
        String commitId = "commit-key";
        String expectedKey = remember(prefix, commitId);

        runContext(prefix, Duration.ofMinutes(1), context -> {
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            CacheManager authCacheManager = context.getBean("cacheManager", CacheManager.class);
            CacheManager commitCacheManager = context.getBean("commitContentCacheManager", CacheManager.class);

            authCacheManager.getCache("signupCodeCache").put("auth-key", "123456");
            cache.get(commitId, this::assembledContent);

            assertThat(authCacheManager)
                    .isInstanceOf(SimpleCacheManager.class)
                    .isNotSameAs(commitCacheManager);
            assertThat(authCacheManager.getCache("signupCodeCache"))
                    .isInstanceOf(CaffeineCache.class);
            assertThat(redisKeys(context.getBean(RedisConnectionFactory.class)))
                    .containsExactly(expectedKey)
                    .allMatch(key -> key.startsWith(BASE_PREFIX))
                    .noneMatch(key -> key.contains("signupCodeCache") || key.contains("auth-key"));
        });
    }

    @Test
    @DisplayName("설정한 TTL이 지나면 실제 Redis 항목이 만료되어 loader를 다시 실행한다")
    void expiredEntryReloadsAfterRedisTtl() {
        String prefix = uniquePrefix();
        String commitId = "commit-ttl";
        String redisKey = remember(prefix, commitId);

        runContext(prefix, Duration.ofSeconds(1), context -> {
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
            AtomicInteger loadCount = new AtomicInteger();

            cache.get(commitId, () -> contentWithRevision(loadCount.incrementAndGet()));
            await().atMost(Duration.ofSeconds(4))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> assertThat(redisKeys(connectionFactory)).doesNotContain(redisKey));

            List<Map<String, Object>> reloaded =
                    cache.get(commitId, () -> contentWithRevision(loadCount.incrementAndGet()));

            assertThat(loadCount).hasValue(2);
            assertThat(reloaded).isEqualTo(contentWithRevision(2));
        });
    }

    @Test
    @DisplayName("Redis 프로세스 중단 시 loader로 응답하고 프로세스 재시작 후 같은 연결로 다시 적중한다")
    void fallsBackDuringProcessRestartAndReconnects() {
        String prefix = uniquePrefix();
        String outageCommitId = "commit-during-outage";
        String recoveredCommitId = "commit-after-recovery";
        remember(prefix, outageCommitId);
        remember(prefix, recoveredCommitId);

        runContext(prefix, Duration.ofMinutes(1), context -> {
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            RedisConnectionFactory connectionFactory = context.getBean(RedisConnectionFactory.class);
            int mappedPort = REDIS.getMappedPort(6379);
            int originalRedisProcessId = redisProcessId();

            try {
                stopRedisProcess(connectionFactory);
                AtomicInteger outageLoadCount = new AtomicInteger();

                List<Map<String, Object>> fallback = cache.get(outageCommitId, () -> {
                    outageLoadCount.incrementAndGet();
                    return contentWithRevision(1);
                });

                assertThat(fallback).isEqualTo(contentWithRevision(1));
                assertThat(outageLoadCount).hasValue(1);
                assertThat(registry.get("commit_content_cache_get_total")
                        .tag("result", "error").counter().count()).isPositive();
            } finally {
                restoreRedisProcess();
                awaitRedisPing(connectionFactory);
            }

            assertThat(REDIS.getMappedPort(6379)).isEqualTo(mappedPort);
            assertThat(redisProcessId()).isNotEqualTo(originalRedisProcessId);
            AtomicInteger recoveredLoadCount = new AtomicInteger();
            List<Map<String, Object>> first = cache.get(recoveredCommitId, () -> {
                recoveredLoadCount.incrementAndGet();
                return contentWithRevision(2);
            });
            List<Map<String, Object>> second = cache.get(recoveredCommitId, () -> {
                recoveredLoadCount.incrementAndGet();
                return contentWithRevision(3);
            });

            assertThat(first).isEqualTo(contentWithRevision(2));
            assertThat(second).isEqualTo(contentWithRevision(2));
            assertThat(recoveredLoadCount).hasValue(1);
        });
    }

    private void runContext(
            String prefix,
            Duration ttl,
            ContextConsumer<AssertableApplicationContext> assertions
    ) {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        CacheConfig.class,
                        CommitContentCacheConfig.class,
                        CommitContentCache.class,
                        RedisTestConfiguration.class)
                .withPropertyValues(
                        "commit.content.cache.provider=redis",
                        "commit.content.cache.ttl=" + ttl,
                        "commit.content.cache.maximum-size=100",
                        "commit.content.cache.key-prefix=" + prefix)
                .run(assertions);
    }

    private String uniquePrefix() {
        return BASE_PREFIX + UUID.randomUUID() + ":";
    }

    private String remember(String prefix, String commitId) {
        String key = prefix + commitId;
        keysToDelete.add(key);
        return key;
    }

    private List<Map<String, Object>> assembledContent() {
        Map<String, Object> orderedBlock = new LinkedHashMap<>();
        orderedBlock.put("type", "paragraph");
        orderedBlock.put("attrs", Map.of("level", 2, "checked", true));
        orderedBlock.put("children", List.of(
                Map.of("text", "첫 문장", "order", 1),
                Map.of("text", "둘째 문장", "marks", List.of("bold", "code"))));
        orderedBlock.put("visible", true);
        orderedBlock.put("revision", 7);
        return Stream.<Map<String, Object>>of(orderedBlock).toList();
    }

    private List<Map<String, Object>> contentWithRevision(int revision) {
        return Stream.<Map<String, Object>>of(Map.of(
                "type", "paragraph",
                "revision", revision,
                "visible", true,
                "children", List.of(Map.of("text", "본문"))))
                .toList();
    }

    private Set<String> redisKeys(RedisConnectionFactory connectionFactory) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Set<byte[]> keys = connection.keyCommands().keys("*".getBytes(StandardCharsets.UTF_8));
            return keys.stream()
                    .map(key -> new String(key, StandardCharsets.UTF_8))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private void ensureRedisRunning() {
        var state = REDIS.getDockerClient()
                .inspectContainerCmd(REDIS.getContainerId())
                .exec()
                .getState();
        if (Boolean.TRUE.equals(state.getPaused())) {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        if (!Boolean.TRUE.equals(state.getRunning())) {
            REDIS.getDockerClient().startContainerCmd(REDIS.getContainerId()).exec();
        }
        restoreRedisProcess();
    }

    private void stopRedisProcess(RedisConnectionFactory connectionFactory) {
        try {
            REDIS.execInContainer("touch", REDIS_STOP_FLAG);
            REDIS.execInContainer("redis-cli", "shutdown", "nosave");
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 프로세스를 중단하지 못했습니다", exception);
        }
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !isRedisProcessRunning());
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !canPing(connectionFactory));
    }

    private void restoreRedisProcess() {
        try {
            REDIS.execInContainer("rm", "-f", REDIS_STOP_FLAG);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 재시작 flag를 제거하지 못했습니다", exception);
        }
        awaitRedisProcess();
        LettuceConnectionFactory connectionFactory = connectionFactory();
        try {
            awaitRedisPing(connectionFactory);
        } finally {
            connectionFactory.destroy();
        }
    }

    private boolean canPing(RedisConnectionFactory connectionFactory) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return "PONG".equals(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void awaitRedisPing(RedisConnectionFactory connectionFactory) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> {
                    try (RedisConnection connection = connectionFactory.getConnection()) {
                        return "PONG".equals(connection.ping());
                    }
                });
    }

    private void awaitRedisProcess() {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> {
                    ExecResult result = REDIS.execInContainer("redis-cli", "ping");
                    return result.getExitCode() == 0 && result.getStdout().contains("PONG");
                });
    }

    private int redisProcessId() {
        try {
            ExecResult result = REDIS.execInContainer("pidof", "redis-server");
            assertThat(result.getExitCode()).isZero();
            return Integer.parseInt(result.getStdout().trim().split("\\s+")[0]);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis process ID를 조회하지 못했습니다", exception);
        }
    }

    private boolean isRedisProcessRunning() {
        try {
            return REDIS.execInContainer("pidof", "redis-server").getExitCode() == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private static LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(IO_TIMEOUT)
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(IO_TIMEOUT).build())
                        .build())
                .build();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standalone, client);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestConfiguration {

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return connectionFactory();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
