# 커밋 본문 캐시 비교 실험 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 단일 애플리케이션에서 캐시 없음·Caffeine·Redis의 커밋 본문 조회 성능과 장애 동작을 동일 조건으로 측정하고, 사전에 합의한 기준으로 한 가지 결론을 선택한다.

**Architecture:** 기존 MySQL 커밋 존재 확인 뒤 `commitMongoId`를 key로 조립 완료 본문을 조회하는 cache-aside 계층을 둔다. 기존 인증용 Caffeine `CacheManager`는 그대로 유지하고 커밋 캐시만 별도 `CacheManager`로 격리하며, provider는 실험 실행 시 `none`, `caffeine`, `redis` 중 하나만 활성화한다. 캐시 오류는 원본 MongoDB 조립 경로로 fail-open하고, 같은 key의 동시 miss는 애플리케이션 내부 single-flight로 합친다. 로컬 측정은 `docker-compose.local.yml`의 단일 앱 서비스가 현재 소스를 빌드하고 provider별 재생성을 담당한다.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Cache, Caffeine, Spring Data Redis, Micrometer/Prometheus, JUnit 5, Mockito, Testcontainers, k6, Node.js 내장 테스트 러너, Docker Compose

---

## 실행 원칙

- 설계 원문: `docs/superpowers/specs/2026-08-01-commit-content-cache-benchmark-design.md`
- 사용자 검토 문서: `docs/superpowers/specs/2026-08-01-commit-content-cache-benchmark-design.html`
- API 계약, MySQL schema, MongoDB collection은 변경하지 않는다.
- 성능 비교 전환 설정은 실험용이다. 결과가 나오기 전에 Redis를 운영 정답으로 표현하지 않는다.
- 저장소 규칙에 따라 AI는 commit, push, merge를 실행하지 않는다. 각 Task 끝에는 추천 커밋 메시지만 기록하고 사용자가 명시적으로 요청할 때만 Git 작업을 수행한다.
- 현재 사용자 소유 미추적 파일 `src/test/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxRelayUnitTest.java`는 수정하거나 stage하지 않는다.

## 파일 책임 지도

### 생성

- `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheProperties.java`
  - provider, TTL, 최대 항목 수, Redis key prefix를 타입 안전하게 보관한다.
- `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfig.java`
  - none/Caffeine/Redis 전용 `commitContentCacheManager`를 조건부 생성하고 인증 캐시와 격리한다.
- `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCache.java`
  - cache-aside, fail-open, single-flight, metric을 한곳에서 담당한다.
- `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheTest.java`
  - provider 공통 동작을 mock cache로 검증한다.
- `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfigTest.java`
  - provider 설정과 인증 CacheManager 격리를 검증한다.
- `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentRedisIntegrationTest.java`
  - 실제 Redis 컨테이너에서 JSON, TTL, 중지·복구를 검증한다.
- `perf/seed/commit_block_plan.mjs`
  - 변경률에 따라 새 block과 재사용 blockOrders를 결정적으로 계산한다.
- `perf/seed/commit_block_plan.test.mjs`
  - 100%/10% 변경, 회전 구간, 길이 보존을 Node 내장 테스트로 검증한다.
- `perf/read/commit_content_benchmark.js`
  - Cold/Hot/Mixed/Cold burst/포화 시나리오를 실행하고 원본 JSON을 저장한다.
- `perf/read/compare_commit_cache_results.mjs`
  - 3회 결과의 변동폭과 최소 의미 차이를 계산해 비교표를 만든다.
- `perf/read/run_commit_cache_matrix.sh`
  - provider·block 수·패턴·반복 번호를 명시적으로 받아 한 조건을 재현한다.
- `perf/read/run_commit_cache_matrix.test.sh`
  - 외부 명령을 stub으로 바꿔 입력 검증, provider별 Compose 분기, prefix 한정 Redis 정리와 비밀값 비기록을 검증한다.
- `perf/read/measurement_gate.mjs`
  - localhost에서 k6 setup 완료와 runner snapshot 완료 사이를 동기화한다.
- `perf/read/results/commit-cache/.gitkeep`
  - 원본 결과 저장 위치를 고정한다.
- `docs/performance/commit-content-cache-result.md`
  - 측정 완료 후 환경, 원본 결과 링크, 판정과 남은 리스크를 기록한다.

### 수정

- `build.gradle`
  - Redis runtime과 Testcontainers 테스트 의존성을 추가한다.
- `src/main/java/io/ejangs/docsa/global/config/CacheConfig.java`
  - 기존 인증 `cacheManager`의 이름과 동작을 유지하고 `@Primary`만 추가한다.
- `src/main/java/io/ejangs/docsa/domain/commit/app/CommitService.java`
  - assembler 직접 호출을 커밋 캐시 경유로 바꾸고 삭제 후 best-effort evict를 호출한다.
- `src/test/java/io/ejangs/docsa/domain/commit/app/GetCommitMockTest.java`
  - 조회가 `CommitContentCache.get`을 경유하는지 검증한다.
- `src/test/java/io/ejangs/docsa/domain/commit/app/DeleteCommitIntegrationTest.java`
  - 삭제된 MySQL 커밋은 캐시 항목이 남아도 반환되지 않는다는 안전성을 검증한다.
- `src/main/resources/application.yml`
  - 기본 provider `none`, TTL, 최대 항목 수, Redis timeout과 key prefix를 선언한다.
- `src/test/resources/application-test.yml`
  - 일반 회귀 테스트는 Redis 없이 `none`으로 실행한다.
- `infra/docker-compose.local.yml`
  - 현재 소스를 빌드하는 단일 로컬 앱과 profile 기반 Redis를 제공한다.
- `perf/seed/seed_dataset.js`
  - `COMMIT_BLOCK_CHANGE_RATE`와 결정적 block 재사용을 적용한다.
- `perf/read/README.md`
  - 데이터 생성, 실행 순서, 장애 검증, 결과 해석 명령을 추가한다.
- `.gitignore`
  - `.gitkeep`을 제외한 대용량 원본 성능 결과가 실수로 commit되지 않게 한다.

### 결과에 따라 조건부 수정

- Redis가 로컬 판정에서 승리한 경우에만 `infra/docker-compose.stg.yml`과 `src/main/resources/application-stg.yml`에 staging 검증용 Redis 설정을 추가한다.
- Caffeine이 승리하면 staging Redis를 추가하지 않는다.
- 캐시 미도입이 결론이면 실험용 커밋 캐시 Java 코드와 Redis runtime 의존성을 제거하고 benchmark artifact만 남긴다.

---

### Task 0: GitHub Issue로 실험 계약 고정

**Files:**
- Read: `.github/ISSUE_TEMPLATE/*`
- No code changes

- [ ] **Step 1: 기존 이슈와 템플릿 확인**

Run:

```bash
gh issue list --state all --search "commit cache OR 커밋 캐시" --limit 30
rg -n "^name:|^title:|성능|재현|완료" .github/ISSUE_TEMPLATE
```

Expected: 기존 이슈 존재 여부와 사용할 템플릿, 저장소 제목 접두사와 기존 라벨을 확인한다.

- [ ] **Step 2: 이슈가 없으면 사용자 승인과 GitHub 계정 확인**

이슈에는 설계 문서의 목표, 비목표, 데이터셋, 세 후보, 반복 판정식, Redis 선택 조건, raw result 경로를 그대로 요약한다. assignee는 요청자의 계정이 확인된 뒤에만 지정한다.

- [ ] **Step 3: 구현 전 승인 gate 기록**

Expected: 생성되거나 확인된 Issue 번호가 이 계획의 실행 기록에 남고, API/DB 무변경과 단일 인스턴스 범위가 이슈에 명시된다.

추천 커밋: 없음

---

### Task 1: 결정적 증분 block 계획을 테스트 우선으로 분리

**Files:**
- Create: `perf/seed/commit_block_plan.mjs`
- Create: `perf/seed/commit_block_plan.test.mjs`
- Modify: `perf/seed/seed_dataset.js`

- [ ] **Step 1: 실패하는 순수 함수 테스트 작성**

`perf/seed/commit_block_plan.test.mjs`에 다음 네 경우를 작성한다.

```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import { buildCommitBlockPlan } from './commit_block_plan.mjs';

test('첫 커밋은 변경률과 무관하게 모든 block을 생성한다', () => {
  const plan = buildCommitBlockPlan({ key: 'u001d001', commitIndex: 0, blockCount: 10,
    changeRate: 0.1, previousBlockIds: [] });
  assert.equal(plan.blocks.length, 10);
  assert.equal(plan.blockOrders.length, 10);
});

test('10% 변경은 기존 id 9개와 신규 id 1개를 사용한다', () => {
  const previous = Array.from({ length: 10 }, (_, i) => `old-${i}`);
  const plan = buildCommitBlockPlan({ key: 'u001d001', commitIndex: 1, blockCount: 10,
    changeRate: 0.1, previousBlockIds: previous });
  assert.equal(plan.blocks.length, 1);
  assert.equal(plan.blockOrders.filter((id) => previous.includes(id)).length, 9);
});

test('변경 구간은 commit 번호에 따라 회전한다', () => {
  const previous = Array.from({ length: 10 }, (_, i) => `old-${i}`);
  const first = buildCommitBlockPlan({ key: 'k', commitIndex: 1, blockCount: 10,
    changeRate: 0.2, previousBlockIds: previous });
  const second = buildCommitBlockPlan({ key: 'k', commitIndex: 2, blockCount: 10,
    changeRate: 0.2, previousBlockIds: first.blockOrders });
  assert.notDeepEqual(first.changedIndexes, second.changedIndexes);
});

test('변경률 1.0은 기존 seed와 같이 매번 전체 block을 생성한다', () => {
  const previous = Array.from({ length: 10 }, (_, i) => `old-${i}`);
  const plan = buildCommitBlockPlan({ key: 'k', commitIndex: 3, blockCount: 10,
    changeRate: 1, previousBlockIds: previous });
  assert.equal(plan.blocks.length, 10);
  assert.equal(plan.blockOrders.length, 10);
});
```

- [ ] **Step 2: 테스트가 함수 부재로 실패하는지 확인**

Run: `node --test perf/seed/commit_block_plan.test.mjs`

Expected: `ERR_MODULE_NOT_FOUND` 또는 `buildCommitBlockPlan` 미정의로 FAIL.

- [ ] **Step 3: 최소 순수 함수 구현**

`buildCommitBlockPlan`은 `changeRate`가 0 초과 1 이하인지, 이전 ID 길이가 0 또는 `blockCount`인지 검사한다. 첫 커밋은 전체를 새로 만들고 이후에는 `Math.max(1, Math.round(blockCount * changeRate))`개 index를 `(commitIndex * changedCount) % blockCount`부터 순환 선택한다. 신규 editor id는 `${key}-c${commitIndex}-b${index}`로 만들고, `blocks`에는 신규 block만, `blockOrders`에는 전체 editor id를 반환한다.

```javascript
export function buildCommitBlockPlan({ key, commitIndex, blockCount, changeRate, previousBlockIds }) {
  if (!(changeRate > 0 && changeRate <= 1)) throw new Error('changeRate must be > 0 and <= 1');
  if (previousBlockIds.length !== 0 && previousBlockIds.length !== blockCount) {
    throw new Error('previousBlockIds length must be 0 or blockCount');
  }
  const firstCommit = previousBlockIds.length === 0;
  const changedCount = firstCommit ? blockCount : Math.max(1, Math.round(blockCount * changeRate));
  const start = firstCommit ? 0 : (commitIndex * changedCount) % blockCount;
  const changedIndexes = Array.from({ length: changedCount }, (_, offset) => (start + offset) % blockCount);
  const changed = new Set(changedIndexes);
  const blockOrders = Array.from({ length: blockCount }, (_, index) =>
    changed.has(index) ? `${key}-c${commitIndex}-b${index}` : previousBlockIds[index]);
  const blocks = changedIndexes.map((index) => ({
    id: blockOrders[index],
    type: 'paragraph',
    data: { text: `${key}-c${commitIndex}-text-${index}` },
  }));
  return { blocks, blockOrders, changedIndexes };
}
```

- [ ] **Step 4: 순수 함수 테스트 통과 확인**

Run: `node --test perf/seed/commit_block_plan.test.mjs`

Expected: 4 tests PASS.

- [ ] **Step 5: seed 스크립트가 순수 함수를 사용하도록 변경**

`COMMIT_BLOCK_CHANGE_RATE` 기본값을 `1.0`으로 읽고, main branch별 `previousBlockIds`를 다음 호출에 넘긴다. feature branch는 분기 기준 main commit의 blockOrders 복사본으로 시작한다. `Math.random()` 기반 ID를 제거해 동일 입력이 동일 데이터 구조를 만든다.

- [ ] **Step 6: k6 문법과 기존 기본 동작 확인**

Run:

```bash
k6 inspect perf/seed/seed_dataset.js
node --test perf/seed/commit_block_plan.test.mjs
```

Expected: k6 inspect 성공, Node 4 tests PASS.

추천 커밋 메시지: `test: add deterministic incremental commit seed`

---

### Task 2: 커밋 캐시 provider 설정과 인증 캐시 격리

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheProperties.java`
- Create: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfig.java`
- Modify: `src/main/java/io/ejangs/docsa/global/config/CacheConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`
- Create: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfigTest.java`

- [ ] **Step 1: 설정 격리 테스트 작성**

`ApplicationContextRunner`로 `none`, `caffeine`, `redis` 각각에서 기존 `cacheManager`와 `commitContentCacheManager`가 별도 bean인지 확인한다. 기본값은 `none`이고 인증 cache 네 개가 모두 유지되는지 검증한다.

```java
assertThat(context).hasBean("cacheManager");
assertThat(context).hasBean("commitContentCacheManager");
assertThat(context.getBean("cacheManager"))
        .isNotSameAs(context.getBean("commitContentCacheManager"));
```

- [ ] **Step 2: 설정 테스트가 새 bean 부재로 실패하는지 확인**

Run: `./gradlew test --tests '*CommitContentCacheConfigTest'`

Expected: `commitContentCacheManager` bean 부재로 FAIL.

- [ ] **Step 3: 의존성과 타입 안전한 속성 추가**

`build.gradle`:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:testcontainers'
```

`CommitContentCacheProperties`는 다음 값을 가진다.

```java
@ConfigurationProperties("commit.content.cache")
public record CommitContentCacheProperties(
        Provider provider,
        Duration ttl,
        long maximumSize,
        String keyPrefix
) {
    public enum Provider { NONE, CAFFEINE, REDIS }
}
```

`application.yml` 기본값:

```yaml
commit:
  content:
    cache:
      provider: ${COMMIT_CONTENT_CACHE_PROVIDER:none}
      ttl: ${COMMIT_CONTENT_CACHE_TTL:PT10M}
      maximum-size: ${COMMIT_CONTENT_CACHE_MAXIMUM_SIZE:400}
      key-prefix: ${COMMIT_CONTENT_CACHE_KEY_PREFIX:docsa:experiment:commit-content:}
spring:
  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL:redis://localhost:6379}
      connect-timeout: ${SPRING_DATA_REDIS_CONNECT_TIMEOUT:200ms}
      timeout: ${SPRING_DATA_REDIS_TIMEOUT:200ms}
```

- [ ] **Step 4: provider별 전용 CacheManager 구성**

- `none`: `NoOpCacheManager`
- `caffeine`: TTL 10분, maximumSize 400의 `CaffeineCacheManager`
- `redis`: `RedisCacheManager`와 `GenericJackson2JsonRedisSerializer`, key prefix, TTL 10분
- 세 조건부 설정은 모두 bean 이름을 `commitContentCacheManager`로 제공한다.
- 기존 `CacheConfig.cacheManager()`의 bean 이름은 변경하지 않고 `@Primary`만 붙인다.
- `AuthService`는 수정하지 않는다. 기존 타입 주입은 primary인 인증 `cacheManager`를 계속 받는다.

- [ ] **Step 5: 설정과 인증 회귀 테스트 확인**

Run:

```bash
./gradlew test --tests '*CommitContentCacheConfigTest' --tests '*AuthServiceTest'
```

Expected: 설정 격리 테스트와 기존 인증 캐시 테스트 PASS.

추천 커밋 메시지: `feat: isolate commit content cache providers`

---

### Task 3: Fail-Open과 single-flight cache-aside 구현

**Files:**
- Create: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCache.java`
- Create: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheTest.java`

- [ ] **Step 1: provider 공통 실패 테스트 작성**

Mock `CacheManager`, `Cache`, `MeterRegistry`를 사용해 다음 테스트를 각각 독립적으로 작성한다.

```text
첫 조회 miss → loader 1회 → put → 결과 반환
두 번째 조회 hit → loader 추가 호출 없음
cache.get 예외 → loader 결과 반환
cache.put 예외 → loader 결과 반환
loader 예외 → put하지 않고 같은 예외 전달
서로 다른 key → 서로 다른 값
같은 key 동시 20요청 → loader 1회
evict 예외 → 호출자에게 전파하지 않음
```

동시성 테스트는 `CountDownLatch`로 20개 요청이 loader 앞에서 동시에 만나게 하고 `AtomicInteger`가 최종 1인지 검증한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*CommitContentCacheTest'`

Expected: `CommitContentCache` 타입 부재로 FAIL.

- [ ] **Step 3: cache-aside 최소 구현**

공개 API를 다음으로 고정한다.

```java
public List<Map<String, Object>> get(
        String commitMongoId,
        Supplier<List<Map<String, Object>>> loader);

public void evict(String commitMongoId);
```

내부에는 `ConcurrentHashMap<String, CompletableFuture<List<Map<String, Object>>>> inFlight`를 둔다. 순서는 다음과 같다.

```text
1. cache.get(key, List.class)
2. hit면 hit counter 후 반환
3. miss면 miss counter
4. putIfAbsent로 key별 CompletableFuture 생성
5. 기존 future가 있으면 join
6. 생성자만 cache를 한 번 재확인
7. loader 실행 시간을 Timer에 기록
8. 성공 값만 cache.put
9. future complete 후 map에서 제거
10. loader 예외는 future에 동일하게 전달하고 캐시하지 않음
```

cache get/put/evict의 `RuntimeException`만 캐시 오류로 기록하고 삼킨다. loader가 던진 `CustomException`과 MongoDB 예외는 캐시 오류로 오인하거나 두 번 실행하지 않는다.

- [ ] **Step 4: metric 이름과 label 고정**

```text
commit_content_cache_get_total{result=hit|miss|error}
commit_content_cache_put_total{result=success|error}
commit_content_cache_eviction_total{result=success|error}
commit_content_assemble_seconds
```

캐시 생성 시 get의 `hit|miss|error`, put과 eviction의 `success|error` counter를 0으로 사전 등록한다. 이벤트가 없었던 label도 Actuator에 명시적 0으로 노출하며 결과 비교기가 누락 series를 0으로 추정하지 않게 한다. key, commitMongoId, 사용자 정보, 본문은 tag와 로그에 넣지 않는다. Redis의 인프라 `evicted_keys`는 애플리케이션 best-effort evict metric과 별도로 결과 수집 단계에서 기록한다.

- [ ] **Step 5: 단위 테스트 통과 확인**

Run: `./gradlew test --tests '*CommitContentCacheTest'`

Expected: 모든 hit/miss/fail-open/single-flight/evict 테스트 PASS.

추천 커밋 메시지: `feat: add fail-open commit content cache`

---

### Task 4: 기존 커밋 조회와 삭제 경로에 연결

**Files:**
- Modify: `src/main/java/io/ejangs/docsa/domain/commit/app/CommitService.java`
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/app/GetCommitMockTest.java`
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/app/DeleteCommitIntegrationTest.java`

- [ ] **Step 1: 조회 경유 테스트를 먼저 변경**

`GetCommitMockTest`에서 `CommitContentAssembler` 대신 `CommitContentCache`를 mock하고 다음 호출을 검증한다.

```java
given(commitContentCache.get(eq(commitMongoId), any())).willReturn(mockContent);
verify(commitContentCache).get(eq(commitMongoId), any());
```

captor로 전달된 `Supplier`를 실행했을 때 `assembler.assemble(commitMongoId)`가 호출되는 별도 테스트를 추가한다.

- [ ] **Step 2: 변경된 테스트 실패 확인**

Run: `./gradlew test --tests '*GetCommitMockTest'`

Expected: `CommitService`가 아직 캐시를 주입받지 않아 FAIL.

- [ ] **Step 3: 조회 경로 최소 변경**

```java
private List<Map<String, Object>> getWholeContent(Long commitId) {
    Commit commit = commitReader.getById(commitId);
    String commitMongoId = commit.getCommitMongoId();
    return commitContentCache.get(commitMongoId, () -> assembler.assemble(commitMongoId));
}
```

문서 권한 확인과 MySQL `commitReader.getById` 순서는 캐시 앞에 그대로 둔다.

- [ ] **Step 4: 삭제 evict 단위 검증 추가**

정상 삭제 테스트에서 삭제 대상 `commitMongoId`로 `commitContentCache.evict`가 호출되는지 확인하고, evict 예외는 `CommitContentCache` 내부에서 처리되므로 서비스 트랜잭션 계약을 바꾸지 않는다.

- [ ] **Step 5: stale cache 안전성 통합 테스트 추가**

삭제 전에 대상 본문을 캐시에 넣고 `deleteCommit`을 호출한다. 이후 같은 API/service 조회는 MySQL `COMMIT_NOT_FOUND`를 반환하며 캐시 본문을 반환하지 않는지 확인한다. 이 테스트는 “evict 성공”뿐 아니라 “MySQL 확인이 캐시보다 앞”이라는 안전장치를 검증한다.

- [ ] **Step 6: 커밋 회귀 테스트 실행**

Run:

```bash
./gradlew test --tests '*GetCommitMockTest' --tests '*DeleteCommitIntegrationTest' --tests '*CommitContentAssemblerTest'
```

Expected: 모두 PASS.

추천 커밋 메시지: `feat: cache assembled commit content reads`

---

### Task 5: 실제 Redis 직렬화·TTL·중지·복구 검증

**Files:**
- Create: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentRedisIntegrationTest.java`
- Modify: `infra/docker-compose.local.yml`

- [ ] **Step 1: Testcontainers 기반 실패 테스트 작성**

`GenericContainer<?>`로 고정 major 이미지 `redis:7.4-alpine`을 시작하고 `@DynamicPropertySource`로 다음을 주입한다.

```text
commit.content.cache.provider=redis
commit.content.cache.ttl=PT2S
spring.data.redis.host=<container host>
spring.data.redis.port=<mapped port>
```

검증 항목:

```text
중첩 List<Map<String,Object>> deep equality
key가 docsa:experiment:commit-content: prefix 아래 생성
2초 후 miss와 loader 재실행
container stop 중 cache error 후 loader 성공
container 재시작 후 miss→put→hit 복구
기존 cacheManager가 여전히 Caffeine 기반
```

- [ ] **Step 2: Redis 설정 미완성으로 실패 확인**

Run: `./gradlew test --tests '*CommitContentRedisIntegrationTest'`

Expected: Redis cache bean 또는 직렬화 설정 오류로 FAIL.

- [ ] **Step 3: 직렬화 설정 보정**

`RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())`를 value serializer로 사용하고 null caching을 비활성화한다. key는 String serializer와 고정 prefix를 사용한다.

- [ ] **Step 4: 로컬 Redis profile 추가**

`infra/docker-compose.local.yml`에 다음 성격의 서비스를 추가한다.

```yaml
redis:
  image: redis:7.4-alpine
  container_name: docsa-redis-local
  profiles: ["commit-cache"]
  command: ["redis-server", "--save", "", "--appendonly", "no"]
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 5s
    timeout: 3s
    retries: 10
```

실험용이므로 persistence와 maxmemory eviction을 켜지 않는다.

- [ ] **Step 5: 실제 Redis 통합 테스트 통과 확인**

Run: `./gradlew test --tests '*CommitContentRedisIntegrationTest'`

Expected: JSON/namespace/TTL/fail-open/recovery/auth isolation 테스트 PASS.

추천 커밋 메시지: `test: verify redis commit cache recovery`

---

### Task 6: 커밋 조회 전용 k6 시나리오 구현

**Files:**
- Create: `perf/read/commit_content_benchmark.js`
- Create: `perf/read/results/commit-cache/.gitkeep`
- Modify: `.gitignore`

- [ ] **Step 1: setup에서 대상 커밋을 정확히 수집**

각 사용자로 로그인한 뒤 title prefix로 문서를 찾고 graph API에서 main commit ID를 수집한다. setup 결과는 다음 형태로 고정한다.

```javascript
{
  users: [{ userNo, cookie, commits: [{ docId, commitId }] }],
  allTargets: [{ userNo, cookie, docId, commitId }],
  hotTargets: [{ userNo, cookie, docId, commitId }]
}
```

`hotTargets`는 사용자별 대표 commit 두 개이며, setup/login/graph 호출에는 `op_commit_get` tag를 붙이지 않는다.

- [ ] **Step 2: 응답 사전 검증 모드 구현**

`SCENARIO=verify`는 첫·중간·마지막 commit을 한 번씩 조회하고 status 200, `content.length === BLOCKS_PER_COMMIT`, 첫/중간/마지막 block의 `id`와 `data.text` 존재를 검사한다.

- [ ] **Step 3: Cold/Hot/Mixed 요청 선택 구현**

- Cold: `shared-iterations`, 전체 400 target을 한 번씩만 사용
- Hot: 사용자별 두 target을 반복
- Mixed: 결정적 5회 주기 중 4회는 사용자별 상위 20%, 1회는 나머지 80%
- 모든 부하 요청은 `op=commit_get` tag, `op_commit_get_ms`, `op_commit_get_payload_bytes`, `commit_get_failed`를 기록

- [ ] **Step 4: Cold burst와 포화 시나리오 구현**

- Cold burst: 하나의 대표 key, `per-vu-iterations`, `iterations=1`, VU 10/50/100
- Saturation: `ramping-arrival-rate`, 25→50→100→200 req/s, 각 60초
- scenario는 `SCENARIO` 환경변수로 하나만 활성화하며 setup/warm-up은 측정 trend에서 제외

- [ ] **Step 5: raw result 경로 고정**

```text
perf/read/results/commit-cache/
  <provider>/blocks-<count>/<pattern>/<load-profile>/run-<1|2|3>/summary.json
```

`load-profile`은 VU 시나리오의 `vus-<n>` 또는 포화 시나리오의 `rate-25-50-100-200`으로 실제 부하를 나타낸다. `handleSummary`에는 provider, block count, pattern, load profile, VU/rate, run number를 함께 기록한다. `.gitignore`는 이 디렉터리의 `*.json`을 제외하고 `.gitkeep`은 보존한다.

- [ ] **Step 6: k6 정적 검증**

Run: `k6 inspect perf/read/commit_content_benchmark.js`

Expected: options와 scenario parsing 성공.

추천 커밋 메시지: `perf: add commit content cache workloads`

---

### Task 7: 한 조건 실행기와 관측 snapshot 구현

**Files:**
- Create: `perf/read/run_commit_cache_matrix.sh`
- Create: `perf/read/run_commit_cache_matrix.test.sh`
- Create: `perf/read/measurement_gate.mjs`
- Modify: `perf/read/commit_content_benchmark.js`
- Modify: `perf/read/README.md`
- Modify: `infra/docker-compose.local.yml`

- [ ] **Step 1: 로컬 앱 서비스를 Compose에 추가**

`infra/docker-compose.local.yml`의 `app`은 저장소 루트를 build context로, `infra/backend/Dockerfile`을 Dockerfile로 사용한다. `docsa-app-local` 컨테이너에 `8080`, `9091`을 노출하고 MySQL, MongoDB, MinIO, Mailpit의 Compose 서비스명을 연결한다.

provider 교차 실행 중 seed 보존을 위해 다음 값을 고정한다.

```text
SPRING_PROFILES_ACTIVE=local
SPRING_JPA_HIBERNATE_DDL_AUTO=update
MONGO_LOCAL_CLEANUP_ENABLED=false
PERF_SEED_USER_COUNT=0
COMMIT_CONTENT_CACHE_PROVIDER=${COMMIT_CONTENT_CACHE_PROVIDER:-none}
SPRING_DATA_REDIS_URL=redis://redis:6379
```

Mongo URI는 `mongodb://mongo:27017/docsa-local?replicaSet=rs0&directConnection=true`를 사용한다. 앱은 Redis에 `depends_on`하지 않아 `none`과 `caffeine`이 Redis 없이 기동하고, runner가 `redis` 조건에서만 `commit-cache` profile의 Redis를 먼저 준비한다.

Run:

```bash
docker compose -f infra/docker-compose.local.yml config
docker compose -f infra/docker-compose.local.yml --profile commit-cache config
```

Expected: 기본 config에 app은 포함되고 Redis는 제외되며, profile config에는 app과 Redis가 모두 포함된다.

- [ ] **Step 2: runner 안전장치 실패 테스트 작성**

`perf/read/run_commit_cache_matrix.test.sh`는 임시 PATH에 `docker`, `curl`, `k6` stub을 만들고 실제 외부 서비스를 건드리지 않은 채 다음을 검증한다.

```text
필수 입력 누락 시 외부 명령 실행 전 실패
허용하지 않은 provider와 run 번호 거부
none/caffeine은 Redis profile을 시작하지 않음
redis만 commit-cache profile을 시작하고 Redis key prefix scan/delete 수행
FLUSHDB/FLUSHALL 명령 부재
environment.txt에 비밀번호·cookie·재시작 명령 값 부재
Cold/Cold burst는 warm-up 없이 실행
Hot/Mixed는 verify 뒤 별도 warm-up과 본 측정 실행
본 k6 setup 완료 → before snapshot → gate 해제 → scenario 시작 순서
SCAN 실패와 after snapshot 일부 실패가 workload 상태를 숨기지 않음
기존 결과 디렉터리 재사용 거부
```

Run: `bash perf/read/run_commit_cache_matrix.test.sh`

Expected: runner 부재 또는 요구 동작 미구현으로 FAIL.

- [ ] **Step 3: 필수 입력과 안전장치 작성**

스크립트는 `PROVIDER`, `BLOCKS_PER_COMMIT`, `PATTERN`, `RUN_NO`, `BASE_URL`을 필수로 받고 provider가 `none|caffeine|redis`, run이 `1|2|3`인지 검사한다. Redis 정리는 전체 DB가 아니라 실험 prefix의 key만 대상으로 한다.

- [ ] **Step 4: 실행 전 환경 기록**

결과 디렉터리에 다음을 저장한다.

```text
git-revision.txt
environment.txt
prometheus-before.txt
container-stats-before.txt
redis-info-before.txt  # Redis 조건만
```

비밀 환경변수 값은 저장하지 않고 변수 이름과 비민감 설정값만 기록한다.

- [ ] **Step 5: provider별 재시작과 준비 확인**

runner는 외부 재시작 hook을 받지 않는다. `COMMIT_CONTENT_CACHE_PROVIDER`를 export한 뒤 `docker compose -f infra/docker-compose.local.yml up -d --build --force-recreate app`으로 앱을 재생성하고 `http://localhost:9091/actuator/health`를 bounded retry한다. `redis` 조건은 profile Redis를 먼저 시작하고 health를 확인한다. `none`과 `caffeine`은 Redis를 중지한 상태에서도 앱 health가 성공해야 한다.

- [ ] **Step 6: warm-up과 본 측정 분리**

`verify`를 먼저 실행하고 Hot/Mixed는 별도 짧은 warm-up 후 본 측정을 실행한다. Cold와 Cold burst는 Redis prefix를 비우거나 Caffeine 앱을 재생성한 뒤 warm-up 없이 실행한다.

본 측정은 localhost 전용 `measurement_gate.mjs`와 함께 실행한다. `commit_content_benchmark.js`의 setup은 로그인·graph 탐색과 필요한 cache warm-up을 마친 뒤 gate에 준비 완료를 알리고 bounded release 대기를 한다. runner는 준비 완료를 확인하고 before snapshot을 저장한 다음 gate를 해제한다. k6 scenario는 release 이후에만 시작한다.

- [ ] **Step 7: 실행 후 snapshot 저장**

```text
prometheus-after.txt
container-stats-after.txt
redis-info-after.txt
summary.json
run.log
```

Prometheus snapshot에는 커밋 cache metric, `jvm_memory_*`, `jvm_gc_pause_*`, `http_server_requests_*`만 필터링한다. before는 본 k6 setup과 warm-up이 끝난 뒤, scenario가 시작하기 전에 수집한다.

- [ ] **Step 8: runner 테스트와 README 명령 검증**

README에 400 commit 데이터셋 생성, 100/500/1000 baseline, provider 교차 순서, Redis 중지·복구, staging 제한을 실제 명령으로 기록한다. shellcheck가 있으면 실행하고, 없으면 `bash -n perf/read/run_commit_cache_matrix.sh`를 실행한다.

Expected: runner 안전장치 테스트와 shell syntax PASS, 비밀값과 전체 Redis DB 삭제 명령 없음.

추천 커밋 메시지: `perf: automate commit cache measurements`

---

### Task 8: 3회 결과 비교와 판정 자동화

**Files:**
- Create: `perf/read/compare_commit_cache_results.mjs`
- Create: `perf/read/compare_commit_cache_results.test.mjs`

- [ ] **Step 1: 실패하는 판정식 테스트 작성**

fixture 객체로 다음을 검증한다.

```javascript
assert.equal(repeatVariation([100, 110, 90]), 20 / 100);
assert.equal(minimumMeaningfulDelta(0.04), 0.10);
assert.equal(minimumMeaningfulDelta(0.18), 0.18);
assert.equal(sameDirection([12, 15, 11]), true);
assert.equal(sameDirection([12, -2, 11]), false);
```

- [ ] **Step 2: 함수 부재로 실패 확인**

Run: `node --test perf/read/compare_commit_cache_results.test.mjs`

Expected: module/function 부재로 FAIL.

- [ ] **Step 3: 비교기 구현**

입력은 provider별 3개 `summary.json`이다. 다음을 Markdown table과 JSON으로 출력한다.

```text
p95 3회 값과 평균
처리량 3회 값과 평균
오류율
dropped_iterations
baseline 반복 변동폭
최소 의미 차이=max(10%, 변동폭)
baseline 대비 변화율
3회 방향 일치 여부
Mongo assemble/cache hit/get error/put error delta
heap/GC/Redis memory delta
```

CLI는 `--load-profile`을 필수로 받아 동일한 부하 조건의 3회 결과만 읽는다. 자동 판정은 설계의 순서를 그대로 적용한다. 데이터가 없으면 승자를 추정하지 않고 `INSUFFICIENT_DATA`, 3회 방향이나 성능과 안전 gate가 충돌하면 `INCONCLUSIVE`로 종료한다. JSON에는 후보별 gate와 이유를 구조적으로 남기고 Markdown에는 모든 3회 원시값과 평균을 표시한다.

- [ ] **Step 4: 테스트와 sample 실행 확인**

Run:

```bash
node --test perf/read/compare_commit_cache_results.test.mjs
node perf/read/compare_commit_cache_results.mjs --help
node perf/read/compare_commit_cache_results.mjs \
  --result-root perf/read/results/commit-cache \
  --blocks 500 --pattern hot --load-profile vus-20
```

Expected: tests PASS, 입력 형식과 출력 경로 help 표시.

추천 커밋 메시지: `perf: add evidence-based cache comparison`

---

### Task 9: 기능 회귀와 실험 전 smoke test

**Files:**
- Modify: `src/main/resources/application.yml`
- Test with: `infra/docker-compose.local.yml`
- Test with: `perf/read/run_commit_cache_matrix.sh`

- [ ] **Step 1: Java compile과 집중 테스트**

Run:

```bash
./gradlew compileJava compileTestJava
./gradlew test --tests '*CommitContentCache*' --tests '*GetCommitMockTest' --tests '*DeleteCommitIntegrationTest' --tests '*AuthServiceTest'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 전체 회귀 테스트**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL. 기존 환경 의존 실패가 있으면 캐시 변경과의 인과를 분리해 기록하며 결과를 숨기지 않는다.

- [ ] **Step 3: 작은 API seed smoke**

```bash
RUN_ID=cache-smoke USER_COUNT=1 DOCS_PER_USER=1 MAIN_COMMITS=3 \
FEATURE_BRANCHES=0 BLOCKS_PER_COMMIT=10 COMMIT_BLOCK_CHANGE_RATE=0.1 SEED_VUS=1 \
k6 run perf/seed/seed_dataset.js
```

Expected: 사용자 1명, 문서 1개, main commit 3개 생성; 첫 commit 신규 block 10개, 이후 commit별 신규 block 1개.

- [ ] **Step 4: 선택적 Redis health 정책을 RED-GREEN으로 검증**

RED 근거를 보존한다.

```text
COMMIT_CONTENT_CACHE_PROVIDER=none
Redis container=stopped
GET /actuator/health → HTTP 503, status=DOWN
원인 → RedisHealthIndicator가 redis host 연결 실패를 전체 앱 health에 반영
```

`src/main/resources/application.yml`의 기존 `management` 아래에 다음 최소 설정을 추가한다.

```yaml
management:
  health:
    redis:
      enabled: ${MANAGEMENT_HEALTH_REDIS_ENABLED:false}
```

Compose 앱을 다시 빌드하고 Redis를 중지한 채 `none`, `caffeine` 각각에서 아래를 확인한다.

```bash
curl -fsS http://localhost:9091/actuator/health
```

Expected: HTTP 200과 `status=UP`. `MANAGEMENT_HEALTH_REDIS_ENABLED=true`를 명시한 경우에만 Redis indicator를 다시 활성화할 수 있다. Redis 상태는 앱 health에 합치지 않고 Redis container health 및 `commit_content_cache_get_total{result="error"}`, `commit_content_cache_put_total{result="error"}`로 관측한다.

- [ ] **Step 5: 현재 DB 소유관계와 일치하는 새 smoke 데이터셋 생성**

이전 `create-drop` 실행 뒤 남은 MongoDB read model과 현재 MySQL 사용자 ID가 어긋난 데이터는 재사용하지 않는다. 기존 데이터를 삭제하지 않고 고유한 `RUN_ID`로 새 문서·커밋을 생성한 뒤 verify 응답의 문서와 commit graph가 모두 조회되는지 확인한다.

Expected: workload setup에서 `404 DOCUMENT_NOT_FOUND`가 발생하지 않고 deep validation이 통과한다.

- [ ] **Step 6: 세 provider smoke**

각 provider에서 verify와 Hot 10초를 한 번 실행한다.

Expected: API 응답 deep validation 동일, none은 hit 0, Caffeine/Redis는 warm-up 이후 hit 증가. Redis 조건에서는 warm hit 확인 후 Redis를 중지해도 앱 health와 커밋 조회가 성공하고 cache error가 증가하며, Redis 재시작 후 miss/put/hit 흐름이 회복된다.

추천 커밋 메시지: 없음

---

### Task 10: 로컬 측정 매트릭스 실행

**Files:**
- Write generated artifacts under: `perf/read/results/commit-cache/`

- [ ] **Step 1: 데이터셋 생성과 사전 검증**

각 block 크기별로 사용자 20명 × 문서 2개 × main commit 10개, 변경률 0.1, feature 0 데이터를 생성한다. 변경률 1.0 대조군은 별도 RUN_ID로 한 번 생성한다.

- [ ] **Step 2: block 크기 baseline**

none, 100/500/1000 blocks, VU20, 60초, 각 3회 실행한다.

- [ ] **Step 3: 주 비교**

500 blocks에서 none/Caffeine/Redis × Hot/Mixed × VU20/50/100 × 60초 × 3회를 실행한다.

- [ ] **Step 4: 큰 본문 비교**

1000 blocks에서 세 provider × Hot/Mixed × VU50 × 60초 × 3회를 실행한다.

- [ ] **Step 5: Cold와 Cold burst**

Cold는 400 key 각각 1회, VU50, 각 provider 3회. Cold burst는 같은 key에 VU10/50/100을 적용해 loader 실행 수와 대기 지연을 기록한다.

- [ ] **Step 6: 포화점 탐색**

Mixed 500 blocks에서 25→50→100→200 req/s, 단계별 60초를 provider별 1회 실행한다. dropped iteration, p95/p99 급증, 오류 시작점, Mongo 조립 수, CPU/heap/GC를 기록한다. 앞선 baseline·주 비교·큰 본문·Cold·Cold burst는 계획대로 3회 반복하되, 포화점은 실행 시간이 긴 탐색 조건으로 축소했으므로 반복 신뢰도가 없는 방향성 근거로만 사용하고 단독 채택 근거로 사용하지 않는다.

- [ ] **Step 7: 교차 실행 순서 준수**

```text
1회: none → Caffeine → Redis
2회: Redis → none → Caffeine
3회: Caffeine → Redis → none
```

Expected: 반복 측정 조건의 모든 결과 디렉터리에 summary와 before/after snapshot이 있으며 3회 미완료 조건은 판정 대상에서 제외된다. 포화점은 provider별 1회 결과를 별도 탐색 근거로 표시한다.

추천 커밋 메시지: 없음 — raw 성능 결과는 기본적으로 commit하지 않는다.

---

### Task 11: Redis 장애 검증과 최종 판정

**Files:**
- Create after measurements: `docs/performance/commit-content-cache-result.md`
- Create after measurements: `docs/performance/commit-content-cache-result.html`

- [ ] **Step 1: Redis fail-open 실험**

Redis warm hit 확인 → 부하 중 Redis stop → 원본 응답 성공 확인 → cache error와 Mongo 조립 증가 확인 → Redis start → miss/put/hit 회복을 순서대로 기록한다.

- [ ] **Step 2: 비교기 실행**

각 핵심 조건의 provider별 3개 결과를 `compare_commit_cache_results.mjs`에 전달한다.

Expected: p95를 주 지표로 변동폭, 최소 의미 차이, 방향 일치 여부와 자원 비용이 출력된다.

- [ ] **Step 3: 결정 규칙 적용**

```text
유효한 hit 개선 없음 → 캐시 미도입
Redis가 Caffeine보다 p95/처리량에서 최소 의미 차이 이상 우수 → Redis 후보
Caffeine heap/GC 문제를 Redis가 최소 의미 차이 이상 완화 + fail-open 통과 → Redis 후보
둘의 차이가 최소 의미 차이 미만 → Caffeine
Redis 장애가 정상 조회를 실패시킴 → Redis 제외
```

- [ ] **Step 4: AI용 Markdown과 사용자용 HTML 결과 문서 작성**

두 결과 문서에는 같은 사실과 판정을 사용한다. Markdown은 후속 AI 작업이 근거를 추적할 수 있는 상세 원문으로, HTML은 사람이 핵심 수치·선택 근거·한계를 빠르게 읽을 수 있는 표와 카드 중심 문서로 작성한다. 환경, Git revision과 미커밋 working tree라는 제한, 데이터셋, 실행 매트릭스, raw artifact 경로, p95/처리량/Mongo/heap/GC/Redis memory, cold 회귀, 장애 결과, 선택·기각 근거, 실제 운영 로그 부재를 포함한다. 사용자 승인으로 saturation은 provider별 1회 탐색만 수행했으며 반복 신뢰도가 없다는 점과 partial run-2가 판정에서 제외됐음을 명시한다.

- [ ] **Step 5: 후속 범위 분기**

- no cache: 실험용 runtime 코드를 제거하고 결과/benchmark만 보존
- Caffeine: 선택된 provider만 남기는 `commit-content-cache-adoption-design` 작성
- Redis: staging에 실험용 Redis를 추가하는 변경 영향부터 별도 설계

추천 커밋 메시지: `docs: record commit cache benchmark decision`

---

### Task 12: 선택 후보만 staging에서 방향성 검증

**Files:**
- Conditional modify: `src/main/resources/application-stg.yml`
- Conditional modify: `infra/docker-compose.stg.yml`
- Write generated artifacts under: `perf/read/results/commit-cache/staging/`

- [ ] **Step 1: 로컬 승자 하나만 준비**

Caffeine이면 환경변수만 적용한다. Redis이면 staging compose 변경의 운영 영향, 비밀 설정과 자원 제한을 별도 승인받은 뒤 profile 또는 독립 service로 추가한다. no-cache 결론이면 staging 부하를 실행하지 않는다.

- [ ] **Step 2: 제한된 staging 부하 실행**

```text
500 blocks / Mixed / VU50 / 60초 / 3회
```

오류율이 baseline보다 상승하면 즉시 중단한다. 포화 테스트는 실행하지 않는다.

- [ ] **Step 3: 방향성만 비교**

로컬과 staging 절대 시간을 합치지 않는다. baseline 대비 개선 방향, 오류율, cache hit와 원본 조립 감소가 같은지만 확인한다.

- [ ] **Step 4: 결과 문서 보완**

staging 조건과 방향성, 중단 여부를 `docs/performance/commit-content-cache-result.md`에 추가한다.

추천 커밋 메시지: `docs: add staging cache validation`

---

## 최종 완료 확인

- [ ] API 응답과 DB schema/collection이 변경되지 않았다.
- [ ] 인증 Caffeine 캐시가 커밋 provider와 무관하게 동작한다.
- [ ] none/Caffeine/Redis가 동일 key/value와 데이터셋을 사용한다.
- [ ] cache get/put 장애가 원본 성공을 막지 않는다.
- [ ] 원본 조립 오류는 캐시 오류로 숨기거나 재시도하지 않는다.
- [ ] 같은 key 동시 miss에서 loader 1회가 증명됐다.
- [ ] 10% 증분 seed와 100% 대조군이 자동 검증됐다.
- [ ] 100/500/1000 baseline과 핵심 비교가 각 3회 완료됐다.
- [ ] Redis 장애 중단·복구가 실제 컨테이너에서 검증됐다.
- [ ] 반복 변동폭보다 큰 결과만 개선으로 판정했다.
- [ ] 선택하지 않은 provider를 운영 정답처럼 남기지 않았다.
- [ ] 5,000 blocks, 다중 앱 인스턴스, ETag, eviction/churn 운영 튜닝은 포함하지 않았다.
- [ ] 사용자 소유의 기존 미추적 파일을 건드리지 않았다.
