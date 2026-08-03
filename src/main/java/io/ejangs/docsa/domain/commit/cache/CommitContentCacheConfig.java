package io.ejangs.docsa.domain.commit.cache;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommitContentCacheProperties.class)
public class CommitContentCacheConfig {

    public static final String CACHE_NAME = "commitContentCache";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "commit.content.cache",
            name = "provider",
            havingValue = "none",
            matchIfMissing = true
    )
    static class NoneCacheConfig {

        @Bean
        CacheManager commitContentCacheManager() {
            NoOpCacheManager cacheManager = new NoOpCacheManager();
            cacheManager.getCache(CACHE_NAME);
            return cacheManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "commit.content.cache", name = "provider", havingValue = "caffeine")
    static class CaffeineCacheConfig {

        @Bean
        CacheManager commitContentCacheManager(CommitContentCacheProperties properties) {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_NAME);
            cacheManager.setCaffeine(Caffeine.newBuilder()
                    .expireAfterWrite(properties.ttl())
                    .maximumSize(properties.maximumSize()));
            return cacheManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "commit.content.cache", name = "provider", havingValue = "redis")
    static class RedisCacheConfig {

        @Bean
        CacheManager commitContentCacheManager(
                CommitContentCacheProperties properties,
                RedisConnectionFactory connectionFactory
        ) {
            RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(properties.ttl())
                    .disableCachingNullValues()
                    .computePrefixWith(cacheName -> properties.keyPrefix())
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(commitContentValueSerializer()));

            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(configuration)
                    .initialCacheNames(Set.of(CACHE_NAME))
                    .build();
        }

        static RedisSerializer<Object> commitContentValueSerializer() {
            TypeFactory typeFactory = TypeFactory.defaultInstance();
            JavaType mapType = typeFactory.constructMapType(Map.class, String.class, Object.class);
            JavaType valueType = typeFactory.constructCollectionType(List.class, mapType);
            return new Jackson2JsonRedisSerializer<>(valueType);
        }
    }
}
