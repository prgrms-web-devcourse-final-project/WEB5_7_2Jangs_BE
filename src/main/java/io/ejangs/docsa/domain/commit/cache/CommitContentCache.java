package io.ejangs.docsa.domain.commit.cache;

import java.util.List;
import java.util.Map;

import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommitContentCache {

    private final CommitContentAssembler assembler;

    @Cacheable(
            cacheNames = CommitContentCacheConfig.CACHE_NAME,
            key = "#commitMongoId",
            cacheManager = "commitContentCacheManager",
            sync = true
    )
    public List<Map<String, Object>> get(String commitMongoId) {
        return assembler.assemble(commitMongoId);
    }

    @CacheEvict(
            cacheNames = CommitContentCacheConfig.CACHE_NAME,
            key = "#commitMongoId",
            cacheManager = "commitContentCacheManager"
    )
    public void evict(String commitMongoId) {
    }
}
