# 커밋 본문 캐시 Single-Flight 검증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 `@Cacheable(sync = true)` 기반 Caffeine 구현이 동일 커밋의 Cold Burst 요청을 한 번의 본문 조립으로 합치는지 API 경계에서 검증한다.

**Architecture:** 기존 `cold_burst` k6 시나리오와 measurement gate를 사용한다. 앱 재시작으로 Caffeine을 비운 뒤 동일 커밋에 VU 50 요청을 집중시키고, Prometheus 전후 snapshot의 조립 및 cache 통계로 Single-Flight를 판정한다.

**Tech Stack:** Spring Boot, Spring Cache, Caffeine, Micrometer/Prometheus, k6, Docker Compose

## Global Constraints

- 로컬 500 Block, Caffeine, Cold Burst VU 50을 1회만 실행한다.
- staging 부하와 추가 반복 측정은 실행하지 않는다.
- `maximumWeight`, Redis 및 운영 코드는 변경하지 않는다.
- 단건 p95는 성능 일반화 근거로 사용하지 않는다.
- 사용자가 변경 내용을 검토할 수 있도록 commit하지 않는다.

---

### Task 1: 기존 Single-Flight 계약 확인

**Files:**
- Test: `src/test/java/io/ejangs/docsa/domain/commit/cache/CommitContentCacheTest.java`
- Test: `src/test/java/io/ejangs/docsa/domain/commit/app/DeleteCommitIntegrationTest.java`

**Interfaces:**
- Consumes: `CommitContentCache.get(String)`, `CommitContentCache.evict(String)`
- Produces: 동일 키 동시 요청 1회 조립과 eviction 후 재조립에 대한 자동화 검증 결과

- [x] **Step 1: 캐시 단위 테스트를 재실행한다**

Run: `bash ./gradlew test --tests io.ejangs.docsa.domain.commit.cache.CommitContentCacheTest --rerun-tasks`

Expected: 동일 키 동시 요청 20개에서 `CommitContentAssembler.assemble("commit-1")` 호출이 1회인 테스트를 포함해 모두 PASS.

- [x] **Step 2: 기존 전체 테스트 결과에서 삭제 정합성을 확인한다**

Evidence: 2026-08-03 전체 `bash ./gradlew test --rerun-tasks` 성공 결과.

Expected: 삭제 시 eviction 호출과 삭제된 커밋의 stale cache 미반환 테스트가 PASS.

### Task 2: 현재 어노테이션 구현의 API Cold Burst 검증

**Files:**
- Create: `perf/read/commit-cache/results/final-annotation-single-flight-20260804-r2/caffeine/blocks-500/cold_burst/vus-50/run-1/*`

**Interfaces:**
- Consumes: `PATTERN=cold_burst`, `COLD_BURST_VUS=50`, Prometheus before/after snapshot
- Produces: 요청 50건의 성공 여부와 `commit_content_assemble_seconds_count`, cache hit/miss 증분

- [x] **Step 1: 로컬 Caffeine Cold Burst를 1회 실행한다**

Run: `RESULT_ROOT=perf/read/commit-cache/results/final-annotation-single-flight-20260804-r2 PROVIDER=caffeine BLOCKS_PER_COMMIT=500 PATTERN=cold_burst RUN_NO=1 RUN_ID=commit-cache-sf-b500-20260804 BASE_URL=http://localhost:8080 USER_PASSWORD="$USER_PASSWORD" COLD_BURST_VUS=50 bash perf/read/commit-cache/run_commit_cache_matrix.sh`

- [x] **Step 2: snapshot 차이를 계산해 판정한다**

요청 50/50 성공, miss 1, hit 49, put 1, cache size 1, eviction 0, assemble 1을 확인한다. p95 265.50 ms는 단건 참고값으로만 기록한다.

### Task 3: 결과 문서 반영 및 검증

**Files:**
- Modify: `docs/performance/commit-content-cache-result.md`
- Modify: `docs/performance/commit-content-cache-result.html`

**Interfaces:**
- Consumes: Task 2의 raw artifact와 판정
- Produces: AI용 상세 Markdown과 사람용 HTML의 동일한 결론

- [x] **Step 1: 결과와 제한을 Markdown에 기록한다**

현재 API Cold Burst의 요청 수, 실패 수, assemble/hit/miss/eviction 증분을 기록한다. p95는 Single-Flight의 목적이 아니므로 성능 개선 수치로 일반화하지 않는다.

- [x] **Step 2: 같은 내용을 HTML에 반영한다**

기존 디자인과 문서 구조를 유지하고 새 검증 결과만 수술적으로 추가한다.

- [x] **Step 3: 문서와 전체 변경을 검증한다**

Run: `git diff --check`

Expected: whitespace 오류 없음. 마지막으로 `git status --short`로 모든 변경을 미커밋 상태로 확인한다.
