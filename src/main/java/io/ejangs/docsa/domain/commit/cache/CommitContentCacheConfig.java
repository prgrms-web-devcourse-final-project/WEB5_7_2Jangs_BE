package io.ejangs.docsa.domain.commit.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommitContentCacheProperties.class)
public class CommitContentCacheConfig {

    public static final String CACHE_NAME = "commitContentCache";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "commit.content.cache",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class EnabledCacheConfig {

        @Bean
        CacheManager commitContentCacheManager(CommitContentCacheProperties properties) {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_NAME);
            cacheManager.setCaffeine(Caffeine.newBuilder()
                    .expireAfterAccess(properties.ttl())
                    .maximumSize(properties.maximumSize())
                    .recordStats());
            return cacheManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "commit.content.cache",
            name = "enabled",
            havingValue = "false"
    )
    static class DisabledCacheConfig {

        @Bean
        CacheManager commitContentCacheManager() {
            NoOpCacheManager cacheManager = new NoOpCacheManager();
            cacheManager.getCache(CACHE_NAME);
            return cacheManager;
        }
    }
}
