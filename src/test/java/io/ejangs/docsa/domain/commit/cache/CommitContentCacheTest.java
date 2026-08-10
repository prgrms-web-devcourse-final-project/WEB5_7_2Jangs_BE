package io.ejangs.docsa.domain.commit.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

class CommitContentCacheTest {

    private final List<ExecutorService> executors = new ArrayList<>();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CachingTestConfig.class, CommitContentCacheConfig.class,
                    CommitContentCache.class)
            .withBean(CommitContentAssembler.class, () -> mock(CommitContentAssembler.class))
            .withPropertyValues(
                    "commit.content.cache.ttl=PT10M",
                    "commit.content.cache.maximum-size=400"
            );

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    @DisplayName("첫 조회는 본문을 조립하고 같은 키의 재조회는 캐시 값을 반환한다")
    void firstGetLoadsAndSecondGetReturnsCachedContent() {
        contextRunner.run(context -> {
            CommitContentAssembler assembler = context.getBean(CommitContentAssembler.class);
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            List<Map<String, Object>> expected = content("cached");
            when(assembler.assemble("commit-1")).thenReturn(expected);

            assertThat(cache.get("commit-1")).isEqualTo(expected);
            assertThat(cache.get("commit-1")).isEqualTo(expected);

            verify(assembler, times(1)).assemble("commit-1");
        });
    }

    @Test
    @DisplayName("캐시를 제거하면 다음 조회에서 본문을 다시 조립한다")
    void evictionMakesNextGetLoadAgain() {
        contextRunner.run(context -> {
            CommitContentAssembler assembler = context.getBean(CommitContentAssembler.class);
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            when(assembler.assemble("commit-1"))
                    .thenReturn(content("first"), content("second"));

            assertThat(cache.get("commit-1")).isEqualTo(content("first"));
            cache.evict("commit-1");
            assertThat(cache.get("commit-1")).isEqualTo(content("second"));

            verify(assembler, times(2)).assemble("commit-1");
        });
    }

    @Test
    @DisplayName("같은 키의 동시 요청은 본문을 한 번만 조립한다")
    void sameKeyConcurrentGetsLoadOnce() {
        contextRunner.run(context -> {
            int requestCount = 20;
            CommitContentAssembler assembler = context.getBean(CommitContentAssembler.class);
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            CountDownLatch loaderEntered = new CountDownLatch(1);
            CountDownLatch releaseLoader = new CountDownLatch(1);
            List<Map<String, Object>> expected = content("shared");
            when(assembler.assemble("commit-1")).thenAnswer(invocation -> {
                loaderEntered.countDown();
                assertThat(releaseLoader.await(2, TimeUnit.SECONDS)).isTrue();
                return expected;
            });
            ExecutorService executor = executor(requestCount);
            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                    return cache.get("commit-1");
                }));
            }

            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(loaderEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseLoader.countDown();
            for (Future<List<Map<String, Object>>> future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(expected);
            }
            verify(assembler, times(1)).assemble("commit-1");
        });
    }

    @Test
    @DisplayName("서로 다른 키의 본문 조립은 서로 차단하지 않는다")
    void differentKeysLoadConcurrently() {
        contextRunner.run(context -> {
            CommitContentAssembler assembler = context.getBean(CommitContentAssembler.class);
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            CountDownLatch bothEntered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            when(assembler.assemble(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> {
                        bothEntered.countDown();
                        assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                        return content(invocation.getArgument(0));
                    });
            ExecutorService executor = executor(2);

            Future<List<Map<String, Object>>> first =
                    executor.submit(() -> cache.get("commit-1"));
            Future<List<Map<String, Object>>> second =
                    executor.submit(() -> cache.get("commit-2"));

            assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(content("commit-1"));
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(content("commit-2"));
        });
    }

    @Test
    @DisplayName("본문 조립 실패는 캐시하지 않고 다음 조회에서 다시 시도한다")
    void assemblyFailureIsNotCached() {
        contextRunner.run(context -> {
            CommitContentAssembler assembler = context.getBean(CommitContentAssembler.class);
            CommitContentCache cache = context.getBean(CommitContentCache.class);
            RuntimeException failure = new IllegalStateException("load failed");
            when(assembler.assemble("commit-1"))
                    .thenThrow(failure)
                    .thenReturn(content("retry"));

            assertThatThrownBy(() -> cache.get("commit-1")).isSameAs(failure);
            assertThat(cache.get("commit-1")).isEqualTo(content("retry"));

            verify(assembler, times(2)).assemble("commit-1");
        });
    }

    private ExecutorService executor(int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        executors.add(executor);
        return executor;
    }

    private List<Map<String, Object>> content(String text) {
        return List.of(Map.of("type", "paragraph", "text", text));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingTestConfig {
    }
}
