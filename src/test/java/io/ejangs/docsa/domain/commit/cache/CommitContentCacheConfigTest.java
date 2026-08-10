package io.ejangs.docsa.domain.commit.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.ejangs.docsa.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class CommitContentCacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, CommitContentCacheConfig.class)
            .withPropertyValues(
                    "commit.content.cache.ttl=PT3M",
                    "commit.content.cache.maximum-size=17"
            );

    @Test
    @DisplayName("커밋 본문 캐시는 인증 캐시와 분리된 Caffeine CacheManager를 사용한다")
    void commitContentCacheUsesIsolatedCaffeineCacheManager() {
        contextRunner.run(context -> {
            CacheManager authCacheManager = context.getBean("cacheManager", CacheManager.class);
            CacheManager commitCacheManager = context.getBean(
                    "commitContentCacheManager", CacheManager.class);

            assertThat(context.getBean(CacheManager.class)).isSameAs(authCacheManager);
            assertThat(commitCacheManager)
                    .isInstanceOf(CaffeineCacheManager.class)
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
    @DisplayName("커밋 본문 Caffeine 캐시는 접근 만료, 최대 크기와 통계를 적용한다")
    void caffeineCacheAppliesAccessExpiryMaximumSizeAndStats() {
        contextRunner.run(context -> {
            CacheManager manager = context.getBean("commitContentCacheManager", CacheManager.class);
            Cache cache = manager.getCache(CommitContentCacheConfig.CACHE_NAME);

            assertThat(cache).isInstanceOf(CaffeineCache.class);
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    ((CaffeineCache) cache).getNativeCache();

            assertThat(nativeCache.policy().expireAfterAccess())
                    .hasValueSatisfying(policy -> assertThat(policy.getExpiresAfter())
                            .isEqualTo(Duration.ofMinutes(3)));
            assertThat(nativeCache.policy().expireAfterWrite()).isEmpty();
            assertThat(nativeCache.policy().eviction())
                    .hasValueSatisfying(policy -> assertThat(policy.getMaximum()).isEqualTo(17));

            assertThat(nativeCache.getIfPresent("missing")).isNull();
            nativeCache.put("commit-id", "content");
            assertThat(nativeCache.getIfPresent("commit-id")).isEqualTo("content");
            assertThat(nativeCache.stats().missCount()).isEqualTo(1);
            assertThat(nativeCache.stats().hitCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("커밋 본문 캐시를 비활성화하면 같은 이름의 NoOp 캐시를 제공한다")
    void disabledCacheUsesNoOpCacheManager() {
        contextRunner
                .withPropertyValues("commit.content.cache.enabled=false")
                .run(context -> {
                    CacheManager manager = context.getBean(
                            "commitContentCacheManager", CacheManager.class);

                    assertThat(manager).isInstanceOf(NoOpCacheManager.class);
                    assertThat(manager.getCacheNames())
                            .containsExactly(CommitContentCacheConfig.CACHE_NAME);
                });
    }
}
