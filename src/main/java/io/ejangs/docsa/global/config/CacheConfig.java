package io.ejangs.docsa.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("signupCodeCache", "passCodeCache");
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(3, TimeUnit.MINUTES)
                        .maximumSize(1000)
        );
        return cacheManager;
    }
}
