package io.ejangs.docsa.domain.commit.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commit.content.cache")
public record CommitContentCacheProperties(
        Provider provider,
        Duration ttl,
        long maximumSize,
        String keyPrefix
) {

    public enum Provider {
        NONE,
        CAFFEINE,
        REDIS
    }
}
