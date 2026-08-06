# 커밋 내용 Caffeine 최종 채택 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비교 실험용 none/Caffeine/Redis 캐시 경계를 단일 인스턴스 운영에 맞는 Caffeine 및 Spring Cache 어노테이션 구조로 단순화한다. `none`은 성능 기준선과 즉시 비활성화를 위한 설정으로만 유지한다.

**Architecture:** `CommitContentCache`는 `@Cacheable(sync = true)`와 `@CacheEvict`만 담당하는 얇은 Spring Bean으로 축소한다. `CommitContentAssembler`는 실제 MongoDB 조회 및 조립 시간만 직접 `Timer`로 기록하고, Caffeine은 접근 기반 만료, 최대 크기, 네이티브 통계를 담당한다.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Cache, Caffeine, Micrometer, Prometheus, JUnit 5, Mockito, k6

## 전역 제약

- Redis 비교 결과 문서와 raw artifact는 보존하고 커밋 조회 운영 경로의 Redis 코드만 제거한다.
- 커밋 내용 캐시의 TTL은 우선 `expireAfterAccess` 10분, 최대 크기는 400개로 둔다.
- 랜덤 TTL은 동시 만료가 관측되기 전까지 구현하지 않는다.
- staging 부하 테스트와 saturation 재측정은 실행하지 않는다.
- 최종 대표 부하 테스트는 Hot VU50, Mixed VU50, Cold VU50의 none/Caffeine 각 3회로 제한한다.
- 사용자가 직접 변경을 검토할 수 있도록 add, commit, push를 수행하지 않는다.

---

### Task 1: Caffeine 전용 캐시 구성

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfigTest.java`
- Modify: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheConfig.java`
- Modify: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

**Interfaces:**
- Produces: `commitContentCacheManager`, cache name `commitContentCache`
- Configuration: `commit.content.cache.enabled`, `commit.content.cache.ttl`, `commit.content.cache.maximum-size`

- [x] **Step 1: 실패 테스트 작성**

  기본 구성에서 Caffeine cache manager가 생성되고 `expireAfterAccess`, `maximumSize`, `recordStats`가 적용되는 테스트로 기존 provider별 테스트를 교체한다.

- [x] **Step 2: RED 확인**

  Run: `bash ./gradlew test --tests io.ejangs.docsa.domain.commit.cache.CommitContentCacheConfigTest`

  Expected: 기존 기본 provider가 `NoOpCacheManager`이므로 실패한다.

- [x] **Step 3: 최소 구현**

  `CommitContentCacheConfig`를 Caffeine 중심 구성으로 줄이고 `CommitContentCacheProperties`에서 `provider`, `keyPrefix`를 제거한다. `enabled=false`일 때는 동일한 어노테이션 경계를 유지하는 `NoOpCacheManager`를 사용한다.

- [x] **Step 4: GREEN 확인**

  같은 집중 테스트를 다시 실행해 통과를 확인한다.

### Task 2: 어노테이션 기반 캐시 경계

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheTest.java`
- Modify: `src/main/java/io/ejangs/docsa/domain/commit/cache/CommitContentCache.java`
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/app/GetCommitMockTest.java`
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/app/DeleteCommitIntegrationTest.java`

**Interfaces:**
- Consumes: `CommitContentAssembler.assemble(String)`
- Produces: `CommitContentCache.get(String)`, `CommitContentCache.evict(String)`

- [x] **Step 1: 실패 테스트 작성**

  Spring context와 실제 Caffeine cache를 사용해 첫 조회 적재, 재조회 hit, 동일 키 동시 조회 1회 조립, 서로 다른 키 독립 실행, eviction 후 재적재를 검증한다.

- [x] **Step 2: RED 확인**

  Run: `bash ./gradlew test --tests io.ejangs.docsa.domain.commit.cache.CommitContentCacheTest`

  Expected: 새 `get(String)` API와 어노테이션 동작이 없어 컴파일 또는 assertion이 실패한다.

- [x] **Step 3: 최소 구현**

  `CommitContentCache`에서 직접 Cache API, `ConcurrentHashMap`, `CompletableFuture`, 수동 counter를 제거하고 `@Cacheable(sync = true)` 및 `@CacheEvict`로 교체한다.

- [x] **Step 4: GREEN 확인**

  집중 테스트와 CommitService 관련 테스트를 실행한다.

### Task 3: 조립 Timer를 assembler 경계로 이전

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/commit/app/CommitContentAssemblerTest.java`
- Modify: `src/main/java/io/ejangs/docsa/domain/commit/app/CommitContentAssembler.java`

**Interfaces:**
- Produces: `commit_content_assemble_seconds`

- [x] **Step 1: 실패 테스트 작성**

  assembler 생성 직후 timer count가 0이고 성공 및 예외 조립 시 실제 호출 횟수만큼 count가 증가하는 테스트를 추가한다.

- [x] **Step 2: RED 확인**

  Run: `bash ./gradlew test --tests io.ejangs.docsa.domain.commit.app.CommitContentAssemblerTest`

  Expected: assembler가 Timer를 등록하거나 기록하지 않아 실패한다.

- [x] **Step 3: 최소 구현**

  생성자에서 Timer를 등록하고 `assemble`의 실제 MongoDB 조회 및 조립 구간을 `Timer.record`로 감싼다.

- [x] **Step 4: GREEN 확인**

  assembler 및 캐시 집중 테스트를 다시 실행한다.

### Task 4: Redis 운영 코드 제거와 회귀 검증

**Files:**
- Delete: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentRedisIntegrationTest.java`
- Modify: `build.gradle`
- Modify: `infra/docker-compose.local.yml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`
- Modify: `perf/read/run_commit_cache_matrix.sh`
- Modify: `perf/read/README.md`

**Interfaces:**
- Preserves: 기존 비교 결과 parser와 결과 문서
- Removes: commit content Redis runtime provider 및 장애 실행 경로

- [x] **Step 1: Redis 운영 참조 검사**

  `rg`로 커밋 캐시 Redis 구성과 다른 Redis 사용처를 구분한다.

- [x] **Step 2: 최소 제거**

  커밋 캐시만을 위한 Redis 의존성, local Compose 서비스, provider 환경변수, Redis 통합 테스트를 제거한다. 과거 raw result와 비교 문서는 유지한다.

- [x] **Step 3: 집중 및 전체 검증**

  Run: `bash ./gradlew test`

  Run: `node --test perf/seed/commit_block_plan.test.mjs perf/read/commit_content_benchmark_helpers.test.mjs perf/read/compare_commit_cache_results.test.mjs`

  Run: `bash perf/read/run_commit_cache_matrix.test.sh`

### Task 5: 대표 성능 재검증과 문서 갱신

**Files:**
- Modify: `perf/read/run_commit_cache_matrix.sh`
- Modify: `docs/performance/commit-content-cache-result.md`
- Modify: `docs/performance/commit-content-cache-result.html`

**Interfaces:**
- Inputs: Hot VU50, Mixed VU50, Cold VU50의 none/Caffeine 각 3회
- Outputs: 최종 어노테이션 구현 기준 p95, 처리량, cache stats, assemble timer

- [x] **Step 1: 로컬 smoke**

  Caffeine 앱을 시작하고 `/actuator/prometheus`에서 Caffeine 네이티브 통계와 `commit_content_assemble_seconds`를 확인한다.

- [x] **Step 2: 대표 18조건 실행**

  기존 seed와 runner를 재사용하되 Redis, saturation, staging은 실행하지 않는다. 리뷰에서 발견된 Cold JVM 재시작 비대칭을 수정하고 Cold 6회는 대칭 조건으로 재측정한다.

- [x] **Step 3: 결과 판정**

  none/Caffeine의 3회 짝지은 p95와 처리량을 비교하고 Cold 회귀 재현 여부를 기록한다.

- [x] **Step 4: 문서 갱신**

  실험 단계의 Redis 비교는 보존하고 최종 운영 구조, 새로운 대표 측정값, 남은 한계를 MD/HTML에 반영한다.

- [x] **Step 5: 최종 검증**

  `git diff --check`, 전체 테스트 1회, 결과 JSON 파싱, 문서 결론 일치 여부를 확인한다.
