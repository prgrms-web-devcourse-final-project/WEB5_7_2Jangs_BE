package io.ejangs.docsa.domain.commit.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.ejangs.docsa.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class CommitContentCacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, CommitContentCacheConfig.class);

    @Test
    @DisplayName("기본 provider는 인증 캐시와 분리된 NoOp 커밋 본문 캐시를 구성한다")
    void defaultProviderUsesIsolatedNoOpCacheManager() {
        contextRunner.run(context -> {
            CacheManager authCacheManager = context.getBean("cacheManager", CacheManager.class);
            CacheManager commitCacheManager = context.getBean("commitContentCacheManager", CacheManager.class);

            assertThat(context.getBean(CacheManager.class)).isSameAs(authCacheManager);
            assertThat(commitCacheManager)
                    .isInstanceOf(NoOpCacheManager.class)
                    .isNotSameAs(authCacheManager);
            assertThat(authCacheManager.getCacheNames()).containsExactlyInAnyOrder(
                    "signupCodeCache",
                    "passCodeCache",
                    "pwdResetCodeCache",
                    "resendLimitCache"
            );
            assertThat(commitCacheManager.getCacheNames())
                    .containsExactly(CommitContentCacheConfig.CACHE_NAME);
        });
    }

    @Test
    @DisplayName("Caffeine provider는 설정한 TTL과 최대 항목 수를 적용한다")
    void caffeineProviderAppliesTtlAndMaximumSize() {
        contextRunner
                .withPropertyValues(
                        "commit.content.cache.provider=caffeine",
                        "commit.content.cache.ttl=PT3M",
                        "commit.content.cache.maximum-size=17",
                        "commit.content.cache.key-prefix=ignored:"
                )
                .run(context -> {
                    CacheManager manager = context.getBean("commitContentCacheManager", CacheManager.class);
                    assertThat(manager).isInstanceOf(CaffeineCacheManager.class);

                    Cache cache = manager.getCache(CommitContentCacheConfig.CACHE_NAME);
                    assertThat(cache).isInstanceOf(CaffeineCache.class);
                    cache.put("commit-id", "content");
                    assertThat(cache.get("commit-id", String.class)).isEqualTo("content");

                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                            ((CaffeineCache) cache).getNativeCache();
                    assertThat(nativeCache.policy().expireAfterWrite())
                            .hasValueSatisfying(policy -> assertThat(policy.getExpiresAfter())
                                    .isEqualTo(Duration.ofMinutes(3)));
                    assertThat(nativeCache.policy().eviction())
                            .hasValueSatisfying(policy -> assertThat(policy.getMaximum()).isEqualTo(17));
                });
    }

    @Test
    @DisplayName("Redis provider는 연결 없이 TTL, key prefix와 직렬화 설정을 적용한다")
    void redisProviderAppliesTtlPrefixAndSerializersWithoutConnecting() {
        contextRunner
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withPropertyValues(
                        "commit.content.cache.provider=redis",
                        "commit.content.cache.ttl=PT7M",
                        "commit.content.cache.maximum-size=400",
                        "commit.content.cache.key-prefix=test-prefix:"
                )
                .run(context -> {
                    CacheManager manager = context.getBean("commitContentCacheManager", CacheManager.class);
                    assertThat(manager).isInstanceOf(RedisCacheManager.class);

                    RedisCacheManager redisCacheManager = (RedisCacheManager) manager;
                    assertThat(redisCacheManager.getCacheNames())
                            .containsExactly(CommitContentCacheConfig.CACHE_NAME);

                    RedisCacheConfiguration configuration = redisCacheManager.getCacheConfigurations()
                            .get(CommitContentCacheConfig.CACHE_NAME);
                    assertThat(configuration.getTtlFunction().getTimeToLive("key", "value"))
                            .isEqualTo(Duration.ofMinutes(7));
                    assertThat(configuration.getKeyPrefixFor(CommitContentCacheConfig.CACHE_NAME))
                            .isEqualTo("test-prefix:");
                    assertThat(StandardCharsets.UTF_8.decode(
                            configuration.getKeySerializationPair().write("key")).toString())
                            .isEqualTo("key");
                    List<Map<String, Object>> value = Stream.of(Map.<String, Object>of(
                            "type", "paragraph",
                            "attrs", Map.of("level", 2),
                            "children", List.of(Map.of("text", "본문", "order", 1))
                    )).toList();
                    ByteBuffer serializedValue = configuration.getValueSerializationPair().write(value);
                    assertThat(configuration.getValueSerializationPair().read(serializedValue.duplicate()))
                            .isEqualTo(value);
                    assertThat(configuration.getAllowCacheNullValues()).isFalse();
                });
    }
}
