package io.ejangs.docsa.domain.commit.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class CommitContentCache {

    private static final String GET_METRIC = "commit_content_cache_get_total";
    private static final String PUT_METRIC = "commit_content_cache_put_total";
    private static final String EVICTION_METRIC = "commit_content_cache_eviction_total";
    private static final String ASSEMBLE_TIMER = "commit_content_assemble_seconds";

    private final Cache cache;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, CompletableFuture<List<Map<String, Object>>>> inFlight =
            new ConcurrentHashMap<>();

    public CommitContentCache(
            @Qualifier("commitContentCacheManager") CacheManager cacheManager,
            MeterRegistry meterRegistry
    ) {
        Cache configuredCache = cacheManager.getCache(CommitContentCacheConfig.CACHE_NAME);
        if (configuredCache == null) {
            throw new IllegalStateException(
                    "CacheManager does not provide cache: " + CommitContentCacheConfig.CACHE_NAME);
        }
        this.cache = configuredCache;
        this.meterRegistry = meterRegistry;
        registerResultCounters();
    }

    public List<Map<String, Object>> get(
            String commitMongoId,
            Supplier<List<Map<String, Object>>> loader
    ) {
        List<Map<String, Object>> cached = getCached(commitMongoId);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<List<Map<String, Object>>> newFuture = new CompletableFuture<>();
        CompletableFuture<List<Map<String, Object>>> existingFuture =
                inFlight.putIfAbsent(commitMongoId, newFuture);
        if (existingFuture != null) {
            return await(existingFuture);
        }

        try {
            List<Map<String, Object>> rechecked = getCached(commitMongoId);
            if (rechecked != null) {
                newFuture.complete(rechecked);
            } else {
                loadAndComplete(newFuture, commitMongoId, loader);
            }
            return await(newFuture);
        } finally {
            inFlight.remove(commitMongoId, newFuture);
        }
    }

    public void evict(String commitMongoId) {
        try {
            cache.evict(commitMongoId);
            increment(EVICTION_METRIC, "success");
        } catch (RuntimeException exception) {
            increment(EVICTION_METRIC, "error");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCached(String commitMongoId) {
        try {
            Cache.ValueWrapper wrapper = cache.get(commitMongoId);
            if (wrapper == null || wrapper.get() == null) {
                increment(GET_METRIC, "miss");
                return null;
            }
            List<Map<String, Object>> value = (List<Map<String, Object>>) wrapper.get();
            increment(GET_METRIC, "hit");
            return value;
        } catch (RuntimeException exception) {
            increment(GET_METRIC, "error");
            return null;
        }
    }

    private void loadAndComplete(
            CompletableFuture<List<Map<String, Object>>> future,
            String commitMongoId,
            Supplier<List<Map<String, Object>>> loader
    ) {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            List<Map<String, Object>> loaded;
            try {
                loaded = loader.get();
            } finally {
                sample.stop(meterRegistry.timer(ASSEMBLE_TIMER));
            }
            if (loaded != null) {
                put(commitMongoId, loaded);
            }
            future.complete(loaded);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private void put(String commitMongoId, List<Map<String, Object>> value) {
        try {
            cache.put(commitMongoId, value);
            increment(PUT_METRIC, "success");
        } catch (RuntimeException exception) {
            increment(PUT_METRIC, "error");
        }
    }

    private List<Map<String, Object>> await(
            CompletableFuture<List<Map<String, Object>>> future
    ) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for commit content assembly", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private void increment(String metric, String result) {
        meterRegistry.counter(metric, "result", result).increment();
    }

    private void registerResultCounters() {
        meterRegistry.timer(ASSEMBLE_TIMER);
        meterRegistry.counter(GET_METRIC, "result", "hit");
        meterRegistry.counter(GET_METRIC, "result", "miss");
        meterRegistry.counter(GET_METRIC, "result", "error");
        meterRegistry.counter(PUT_METRIC, "result", "success");
        meterRegistry.counter(PUT_METRIC, "result", "error");
        meterRegistry.counter(EVICTION_METRIC, "result", "success");
        meterRegistry.counter(EVICTION_METRIC, "result", "error");
    }
}
