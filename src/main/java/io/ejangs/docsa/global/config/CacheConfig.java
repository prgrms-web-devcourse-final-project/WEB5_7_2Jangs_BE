package io.ejangs.docsa.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache signupCodeCache = new CaffeineCache(
                "signupCodeCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(4, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache passCodeCache = new CaffeineCache(
                "passCodeCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(4, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache pwdResetCodeCache = new CaffeineCache(
                "pwdResetCodeCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(4, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache resendLimitCache = new CaffeineCache(
                "resendLimitCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(3, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build()
        );

        cacheManager.setCaches(List.of(
                signupCodeCache,
                passCodeCache,
                pwdResetCodeCache,
                resendLimitCache
        ));

        return cacheManager;
    }
}