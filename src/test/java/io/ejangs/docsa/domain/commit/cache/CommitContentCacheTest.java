package io.ejangs.docsa.domain.commit.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleValueWrapper;

class CommitContentCacheTest {

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    @DisplayName("캐시 생성 시 모든 결과 counter를 0으로 사전 등록한다")
    void registersAllResultCountersWithZeroOnCreation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        subject(mock(Cache.class), registry);

        assertThat(counter(registry, "commit_content_cache_get_total", "hit")).isZero();
        assertThat(counter(registry, "commit_content_cache_get_total", "miss")).isZero();
        assertThat(counter(registry, "commit_content_cache_get_total", "error")).isZero();
        assertThat(counter(registry, "commit_content_cache_put_total", "success")).isZero();
        assertThat(counter(registry, "commit_content_cache_put_total", "error")).isZero();
        assertThat(counter(registry, "commit_content_cache_eviction_total", "success")).isZero();
        assertThat(counter(registry, "commit_content_cache_eviction_total", "error")).isZero();
    }

    @Test
    @DisplayName("캐시 생성 직후 본문 조립 timer를 0으로 사전 등록한다")
    void registersAssembleTimerWithZeroOnCreation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        subject(mock(Cache.class), registry);

        assertThat(registry.get("commit_content_assemble_seconds").timer().count()).isZero();
    }

    @Test
    @DisplayName("첫 조회는 본문을 적재해 캐시하고 다음 조회는 캐시 값을 반환한다")
    void missLoadsOnceCachesValueAndNextCallHitsCache() {
        ConcurrentMapCache cache = new ConcurrentMapCache(CommitContentCacheConfig.CACHE_NAME);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);
        AtomicInteger loads = new AtomicInteger();
        List<Map<String, Object>> value = content("first");

        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return value;
        })).isEqualTo(value);
        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return content("unexpected");
        })).isEqualTo(value);

        assertThat(loads).hasValue(1);
        assertThat(cache.get("commit-1").get()).isEqualTo(value);
        assertThat(counter(registry, "commit_content_cache_get_total", "miss")).isEqualTo(2.0);
        assertThat(counter(registry, "commit_content_cache_get_total", "hit")).isEqualTo(1.0);
        assertThat(counter(registry, "commit_content_cache_put_total", "success")).isEqualTo(1.0);
        assertThat(registry.get("commit_content_assemble_seconds").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 커밋 키는 각각의 본문 값을 캐시한다")
    void differentKeysKeepDifferentValues() {
        CommitContentCache subject = subject(
                new ConcurrentMapCache(CommitContentCacheConfig.CACHE_NAME), new SimpleMeterRegistry());

        assertThat(subject.get("commit-1", () -> content("one"))).isEqualTo(content("one"));
        assertThat(subject.get("commit-2", () -> content("two"))).isEqualTo(content("two"));
        assertThat(subject.get("commit-1", () -> content("wrong"))).isEqualTo(content("one"));
        assertThat(subject.get("commit-2", () -> content("wrong"))).isEqualTo(content("two"));
    }

    @Test
    @DisplayName("leader 재확인에서 캐시 값이 발견되면 loader와 put을 실행하지 않는다")
    void leaderRecheckHitReturnsCachedValueWithoutLoadingOrPutting() {
        Cache cache = mock(Cache.class);
        List<Map<String, Object>> cached = content("cached");
        when(cache.get("commit-1"))
                .thenReturn(null, new SimpleValueWrapper(cached));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);
        AtomicInteger loads = new AtomicInteger();

        List<Map<String, Object>> result = subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return content("loaded");
        });

        assertThat(result).isEqualTo(cached);
        assertThat(loads).hasValue(0);
        verify(cache, times(2)).get("commit-1");
        verify(cache, never()).put(any(), any());
        assertThat(registry.get("commit_content_assemble_seconds").timer().count()).isZero();
    }

    @Test
    @DisplayName("캐시 조회 예외가 발생하면 loader 결과를 반환하고 get 오류를 기록한다")
    void cacheGetFailureFallsOpenAndRecordsError() {
        Cache cache = mock(Cache.class);
        when(cache.get("commit-1")).thenThrow(new IllegalStateException("cache unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);

        assertThat(subject.get("commit-1", () -> content("loaded"))).isEqualTo(content("loaded"));

        assertThat(counter(registry, "commit_content_cache_get_total", "error")).isEqualTo(2.0);
        assertThat(counter(registry, "commit_content_cache_put_total", "success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("캐시 저장 예외가 발생해도 loader 결과를 반환하고 put 오류를 기록한다")
    void cachePutFailureFallsOpenAndRecordsError() {
        Cache cache = mock(Cache.class);
        doThrow(new IllegalStateException("cache unavailable")).when(cache).put(any(), any());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);

        assertThat(subject.get("commit-1", () -> content("loaded"))).isEqualTo(content("loaded"));

        assertThat(counter(registry, "commit_content_cache_put_total", "error")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("loader의 RuntimeException은 캐시하지 않고 원본 예외로 재시도마다 전파한다")
    void loaderRuntimeFailureIsNotCachedAndSameInstanceIsRetried() {
        Cache cache = mock(Cache.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);
        RuntimeException failure = new IllegalArgumentException("load failed");
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<Map<String, Object>>> loader = () -> {
            loads.incrementAndGet();
            throw failure;
        };

        assertThatThrownBy(() -> subject.get("commit-1", loader)).isSameAs(failure);
        assertThatThrownBy(() -> subject.get("commit-1", loader)).isSameAs(failure);

        assertThat(loads).hasValue(2);
        verify(cache, never()).put(any(), any());
        assertThat(counter(registry, "commit_content_cache_put_total", "success")).isZero();
        assertThat(counter(registry, "commit_content_cache_put_total", "error")).isZero();
        assertThat(registry.get("commit_content_assemble_seconds").timer().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("loader의 CustomException은 감싸지 않고 동일한 예외로 전파한다")
    void loaderCustomExceptionIsPropagatedWithoutWrapping() {
        CommitContentCache subject = subject(mock(Cache.class), new SimpleMeterRegistry());
        CustomException failure = new CustomException(CommitErrorCode.COMMIT_NOT_FOUND);

        assertThatThrownBy(() -> subject.get("commit-1", () -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    @DisplayName("loader가 직접 던진 CompletionException은 leader에게 동일한 인스턴스로 전파한다")
    void loaderCompletionExceptionIsPropagatedToLeaderWithoutUnwrapping() {
        Cache cache = mock(Cache.class);
        CommitContentCache subject = subject(cache, new SimpleMeterRegistry());
        CompletionException failure = new CompletionException((Throwable) null);
        AtomicInteger loads = new AtomicInteger();

        assertThatThrownBy(() -> subject.get("commit-1", () -> {
            loads.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(loads).hasValue(1);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("loader의 CompletionException은 같은 키 waiter에도 동일하게 전파하고 다음 요청에서 재시도한다")
    void loaderCompletionExceptionReachesWaiterAndClearsInFlightForRetry() throws Exception {
        CountDownLatch allInitialGets = new CountDownLatch(3);
        Cache cache = cacheThatCountsGets(allInitialGets);
        CommitContentCache subject = subject(cache, new SimpleMeterRegistry());
        ExecutorService executor = executor(2);
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CompletionException failure = new CompletionException((Throwable) null);
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<Map<String, Object>>> failingLoader = () -> {
            loads.incrementAndGet();
            loaderEntered.countDown();
            try {
                assertThat(releaseLoader.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            throw failure;
        };

        Future<List<Map<String, Object>>> leader =
                executor.submit(() -> subject.get("commit-1", failingLoader));
        assertThat(loaderEntered.await(2, TimeUnit.SECONDS)).isTrue();
        Future<List<Map<String, Object>>> waiter =
                executor.submit(() -> subject.get("commit-1", failingLoader));
        assertThat(allInitialGets.await(2, TimeUnit.SECONDS)).isTrue();
        releaseLoader.countDown();

        assertThatThrownBy(() -> leader.get(2, TimeUnit.SECONDS)).hasCause(failure);
        assertThatThrownBy(() -> waiter.get(2, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(loads).hasValue(1);
        verify(cache, never()).put(any(), any());

        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return content("retry");
        })).isEqualTo(content("retry"));
        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("loader의 Error는 감싸거나 캐시하지 않고 동일한 인스턴스로 전파한다")
    void loaderErrorIsPropagatedWithoutWrappingOrCaching() {
        Cache cache = mock(Cache.class);
        CommitContentCache subject = subject(cache, new SimpleMeterRegistry());
        AssertionError failure = new AssertionError("load failed");
        AtomicInteger loads = new AtomicInteger();

        assertThatThrownBy(() -> subject.get("commit-1", () -> {
            loads.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(loads).hasValue(1);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("loader가 null을 반환하면 그대로 반환하고 캐시하지 않는다")
    void nullLoaderResultIsReturnedAndNotCached() {
        Cache cache = mock(Cache.class);
        CommitContentCache subject = subject(cache, new SimpleMeterRegistry());
        AtomicInteger loads = new AtomicInteger();

        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();
        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();

        assertThat(loads).hasValue(2);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("같은 키의 동시 요청 20건은 loader를 한 번만 실행하고 같은 본문을 반환한다")
    void sameKeyConcurrentRequestsUseOneLoader() throws Exception {
        int requests = 20;
        CountDownLatch allGets = new CountDownLatch(requests + 1);
        Cache cache = cacheThatCountsGets(allGets);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);
        ExecutorService executor = executor(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        List<Map<String, Object>> expected = content("shared");
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

        for (int index = 0; index < requests; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                return subject.get("commit-1", () -> {
                    loads.incrementAndGet();
                    try {
                        assertThat(releaseLoader.await(2, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return expected;
                });
            }));
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(allGets.await(2, TimeUnit.SECONDS)).isTrue();
        releaseLoader.countDown();

        for (Future<List<Map<String, Object>>> future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(loads).hasValue(1);
        verify(cache, times(1)).put("commit-1", expected);
        assertThat(registry.get("commit_content_assemble_seconds").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 키의 loader는 동시에 실행되어 서로를 차단하지 않는다")
    void differentKeyLoadersDoNotBlockEachOther() throws Exception {
        CommitContentCache subject = subject(mock(Cache.class), new SimpleMeterRegistry());
        ExecutorService executor = executor(2);
        CountDownLatch bothLoadersEntered = new CountDownLatch(2);
        CountDownLatch releaseLoaders = new CountDownLatch(1);
        Supplier<List<Map<String, Object>>> loader = () -> {
            bothLoadersEntered.countDown();
            try {
                assertThat(releaseLoaders.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return content("loaded");
        };

        Future<List<Map<String, Object>>> first = executor.submit(() -> subject.get("commit-1", loader));
        Future<List<Map<String, Object>>> second = executor.submit(() -> subject.get("commit-2", loader));

        assertThat(bothLoadersEntered.await(2, TimeUnit.SECONDS)).isTrue();
        releaseLoaders.countDown();
        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(content("loaded"));
        assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(content("loaded"));
    }

    @Test
    @DisplayName("leader loader가 실패하면 모든 waiter에 실패를 전파하고 다음 요청에서 재시도한다")
    void leaderFailureReachesAllWaitersAndNextCallRetries() throws Exception {
        int requests = 10;
        CountDownLatch allGets = new CountDownLatch(requests + 1);
        Cache cache = cacheThatCountsGets(allGets);
        CommitContentCache subject = subject(cache, new SimpleMeterRegistry());
        ExecutorService executor = executor(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        RuntimeException failure = new IllegalStateException("load failed");
        AtomicInteger loads = new AtomicInteger();
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

        for (int index = 0; index < requests; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return subject.get("commit-1", () -> {
                    loads.incrementAndGet();
                    try {
                        assertThat(releaseLoader.await(2, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    throw failure;
                });
            }));
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(allGets.await(2, TimeUnit.SECONDS)).isTrue();
        releaseLoader.countDown();

        for (Future<List<Map<String, Object>>> future : futures) {
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .hasCause(failure);
        }
        assertThat(loads).hasValue(1);
        assertThat(subject.get("commit-1", () -> {
            loads.incrementAndGet();
            return content("retry");
        })).isEqualTo(content("retry"));
        assertThat(loads).hasValue(2);
    }

    @Test
    @DisplayName("캐시 삭제 성공과 예외를 구분해 기록하고 예외는 호출자에게 전파하지 않는다")
    void evictSucceedsOrFallsOpenAndRecordsResult() {
        Cache cache = mock(Cache.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommitContentCache subject = subject(cache, registry);

        subject.evict("commit-1");
        doThrow(new IllegalStateException("cache unavailable")).when(cache).evict("commit-2");
        subject.evict("commit-2");

        verify(cache).evict("commit-1");
        assertThat(counter(registry, "commit_content_cache_eviction_total", "success")).isEqualTo(1.0);
        assertThat(counter(registry, "commit_content_cache_eviction_total", "error")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("지정한 커밋 본문 캐시가 없으면 구성 오류로 즉시 실패한다")
    void missingNamedCacheFailsFast() {
        CacheManager cacheManager = mock(CacheManager.class);

        assertThatThrownBy(() -> new CommitContentCache(cacheManager, new SimpleMeterRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CommitContentCacheConfig.CACHE_NAME);
    }

    private CommitContentCache subject(Cache cache, SimpleMeterRegistry registry) {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(CommitContentCacheConfig.CACHE_NAME)).thenReturn(cache);
        return new CommitContentCache(cacheManager, registry);
    }

    private Cache cacheThatCountsGets(CountDownLatch gets) {
        Cache cache = mock(Cache.class);
        when(cache.get(any())).thenAnswer(invocation -> {
            gets.countDown();
            return null;
        });
        return cache;
    }

    private ExecutorService executor(int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        executors.add(executor);
        return executor;
    }

    private double counter(SimpleMeterRegistry registry, String name, String result) {
        return registry.get(name).tag("result", result).counter().count();
    }

    private List<Map<String, Object>> content(String text) {
        return List.of(Map.of("type", "paragraph", "text", text));
    }
}
