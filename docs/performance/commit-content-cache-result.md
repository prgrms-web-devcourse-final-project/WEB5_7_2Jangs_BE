# 커밋 본문 캐시 로컬 벤치마크 최종 판정

- 판정일: 2026-08-04 (KST)
- 최종 판정: **단일 인스턴스 Caffeine 조건부 채택 (DONE_WITH_CONCERNS)**
- 결론 한 줄: 수동 Caffeine 비교 실험에서 반복 조회 이득을 확인해 Caffeine을 선택했고, 최종 운영 구현은 Spring Cache 어노테이션으로 단순화했다. Hot/Mixed 개선율은 수동 구현의 측정 근거이며 최종 어노테이션 구현의 성능으로 일반화하지 않는다. 어노테이션 전환 과정의 API 관측에서는 동일 키 Cold Burst 50건에서 본문 조립 1회를 확인했고, 최종 코드의 재현 가능한 근거는 동시성 테스트다. JVM 조건을 대칭으로 맞춘 Cold는 `INCONCLUSIVE`로 판정했다.

## 범위와 재현성

- 전체 raw root: `perf/read/commit-cache/results/` (로컬 전용, `.gitignore` 대상)
- 커밋된 최소 증거: `perf/read/commit-cache/results/evidence/`
- 비교 실험 측정 revision: `62a2d1949ad81a8db75cc1609222b04477cb5c3f` (수동 Caffeine 구현)
- 최종 어노테이션 구현: `8fab327`, 성능 테스트 도구: `4f96481`, 결과 문서: `77eab3c`
- 최종 어노테이션 Cold Burst는 실행 시 working tree 변경을 포함했으므로 hash만으로 그 API 측정을 재현할 수 없다. 해당 결과는 동작 참고 관측으로만 사용하고, 현재 코드의 계약은 커밋된 자동화 테스트로 검증한다.
- dataset: 사용자 20명 × 사용자당 문서 2개 × main commit 10개, 핵심 비교 block 500, main 데이터셋 `run_id=task10-20260802-c-b500-r01` 계열이다. 환경 파일은 `base_url`, health endpoint 및 비민감 구성만 저장하며 민감값은 기록하지 않는다.
- staging 부하 테스트는 사용자 지시로 실행하지 않았고, 이번 작업에서도 실행하지 않았다.

## 2026-08-03 Caffeine 비교 실험과 최종 구현 재검증

비교 실험에서는 수동 캐시 경계와 Caffeine을 사용했고, 이후 최종 운영 코드는 이를 `@Cacheable(sync = true)` 및 `@CacheEvict`로 단순화했다. 캐시 구현은 Caffeine 하나만 남겼고 Redis runtime 의존성·Compose 서비스·통합 테스트를 제거했다. Caffeine은 `expireAfterAccess=10분`, `maximumSize=400`, `recordStats()`를 사용한다. `commit.content.cache.enabled=false`는 성능 baseline과 운영상 즉시 비활성화를 위한 `NoOpCacheManager` 경로다.

MongoDB 조회 및 본문 조립 시간은 캐시 miss에서만 실행되는 `CommitContentAssembler` 내부를 직접 `Timer`로 감쌌다. `@Timed`를 cache proxy 바깥 메서드에 붙이면 hit까지 포함한 전체 메서드 시간을 기록할 수 있어, 실제 DB 조립 비용만 분리하려는 목적과 맞지 않기 때문이다.

- Hot/Mixed raw root: `perf/read/commit-cache/results/final-annotation-20260803-r2/` (12/12, 실제 revision `62a2d19`)
- 대칭 Cold raw root: `perf/read/commit-cache/results/final-annotation-cold-symmetric-20260803/` (6/6, 실제 revision `62a2d19`)
- 검산용 evidence: `perf/read/commit-cache/results/evidence/caffeine-comparison-summary.json`, `caffeine-comparison-prometheus-after.txt`
- 조건: 500 Block, VU 50, Hot/Mixed/Cold, none/Caffeine 각 3회. Cold는 두 provider 모두 verify 후 앱을 동일하게 재시작했다.
- 미실행: staging, saturation, Redis 재측정
- 유효한 18회: `http_req_failed{op:commit_get}`와 `commit_get_failed` 0, dropped iteration 0. 전역 HTTP 실패에는 measurement gate 대기 중 의도된 425가 포함되므로 0이라고 주장하지 않는다.

| 조건 | none p95 | 수동 Caffeine p95 | 3회 평균 p95 변화 | 반복 일관성 | cache 관측 |
| --- | ---: | ---: | ---: | --- | --- |
| Hot VU50 | 182.02 ms | 149.58 ms | **17.8% 개선** | p95 3/3 개선 | hit 19,797~21,467, miss·assemble 0 |
| Mixed VU50 | 187.77 ms | 151.35 ms | **19.4% 개선** | p95 3/3 개선 | hit 20,484~21,689, miss·assemble 0 |
| Cold VU50 | 505.06 ms | 422.34 ms | 평균 16.4% 개선이나 **INCONCLUSIVE** | p95 2/3 개선, baseline 변동폭 약 26% | hit 0, miss·assemble 400, eviction 0 |

`summary.json`의 `iterations.values.rate`는 k6 setup과 measurement gate 대기까지 포함한 전체 실행시간 기준이라 실제 조회 구간 처리량으로 사용하지 않는다. 위 최종 판정은 gate 해제 뒤 요청에 tag된 `op_commit_get_ms` p95만 사용한다.

위 Hot/Mixed 수치는 최종 어노테이션 구현의 재측정 결과가 아니라 `62a2d19`의 수동 Caffeine 비교 실험 결과다. 최종 구현의 성능 개선율로 주장하지 않고, Caffeine 채택을 위한 사전 비교 근거로만 사용한다. 새 checkout에서는 커밋된 evidence 파일로 p95 평균과 개선율을 재계산할 수 있다.

Mixed는 80/20 대상 분포를 사용하지만 runner의 10초 사전 warm-up 동안 전체 working set이 채워져 본 측정 cache miss가 0이었다. 따라서 이 결과는 부분 miss 상황이 아니라 **더 넓은 working set의 steady-state warm 성능**으로 해석한다.

### 현재 어노테이션 구현의 동일 키 Single-Flight 검증

- raw root: `perf/read/commit-cache/results/final-annotation-single-flight-20260804-r2/` (로컬 전용)
- 검산용 evidence: `perf/read/commit-cache/results/evidence/single-flight-summary.json`, `single-flight-prometheus-before.txt`, `single-flight-prometheus-after.txt`
- 조건: 로컬 단일 앱, Caffeine 활성화, 500 Block, 빈 캐시, 동일 커밋 1개, VU 50, VU당 1회 요청
- 데이터셋: 사용자 20명 × 문서 2개 × main commit 10개, `run_id=commit-cache-sf-b500-20260804`
- 결과: 측정 요청 50/50 성공, `commit_get_failed=0`, commit 조회 HTTP failure rate 0, interrupted iteration 0
- cache 증분: miss 1, hit 49, put 1, size 1, eviction 0
- 조립 증분: `commit_content_assemble_seconds_count` 0→1
- 참고 관측: 평균 196.63 ms, p95 265.50 ms, 응답 크기 72,293 bytes

어노테이션 전환 과정의 API 관측에서 동일 키 요청 50건에 대해 `CommitContentAssembler.assemble()` 실행 1회와 hit 49회를 관측했다. 동일 키 Cold Miss의 부하 증폭 방지 계약은 **PASS**로 판정한다. 다만 API 측정 실행 시점의 working tree 변경은 `62a2d19`에 포함되지 않으므로, 최종 코드의 재현 가능한 근거는 커밋된 latch 기반 동시 요청 20개 자동화 테스트다.

이 측정은 Single-Flight 동작 검증을 위한 단건이다. none baseline과 반복 측정이 없으므로 p95 265.50 ms를 성능 개선율이나 운영 절대 성능으로 일반화하지 않는다. `dropped_iterations` key는 없었고 50개 iteration이 모두 완료됐으므로 기존 비교기 규칙에 따라 dropped 0으로 해석한다.

최초 최종 측정과 이전 수동 Caffeine 측정은 Cold에서 Caffeine만 verify 후 앱을 재시작해 `none`에 JVM/JIT 예열 이점이 남는 비대칭이 있었다. 따라서 해당 53.7% 및 약 56% 회귀 수치는 최종 Cold 근거에서 제외한다. 대칭 재측정은 2/3 개선, 1/3 악화였고 평균 개선폭도 baseline 변동폭보다 작아, one-hit 경로는 **체계적 회귀가 확인된 것이 아니라 아직 결론을 낼 수 없는 위험**으로 남긴다.

최종 판정은 **Caffeine 조건부 채택**이다. 반복 조회에서는 3회 모두 의미 있는 개선을 유지했고, 단일 인스턴스에서 Redis의 네트워크·운영 복잡도를 추가할 근거는 없었다. 다만 실제 hit ratio가 낮거나 cold p95가 중요한 서비스라면 캐시가 손해이므로 다음 gate를 둔다.

- Grafana/Prometheus: `cache_gets_total`, `cache_evictions_total`, `cache_size`, `cache_puts_total`, `commit_content_assemble_seconds`
- 경고 관점: hit ratio 하락, eviction 증가, assemble count 재상승, cold p95 상승을 함께 본다.
- 즉시 대응: `COMMIT_CONTENT_CACHE_ENABLED=false`로 비활성화한다.
- 랜덤 TTL: 현재 구현하지 않는다. commit 내용은 불변이고 동시 만료/eviction churn이 관측되지 않았으므로 복잡도를 추가할 근거가 없다.

## 판정 방법과 자동 결과

비교기는 `none`, `caffeine`, `redis`의 같은 조건 3회를 run 번호로 짝지어 p95를 계산한다. baseline 변동폭은 `(max(none p95) - min(none p95)) / mean(none p95)`, 최소 의미 차이는 `max(10%, baseline 변동폭)`이다. 캐시 후보는 방향 일치, 최소 의미 p95 개선, 3회 모두 Mongo assemble 감소/cache hit 관측, 오류 증가 없음, dropped 0, (Redis면 Fail-Open 통과)를 모두 만족해야 한다.

기존 analysis-v1은 `dropped_iterations` key 부재를 미측정으로 처리해 exit 2를 반환했으며 보존했다. 공식 k6 문서는 이 metric을 실행하지 못한 iteration의 Counter로 정의한다. 실제 성공 VU/rate summary에서는 key 부재가 일관되게 재현됐고, 공식 의미 및 TDD 검증 뒤 **유효한 `metrics.metrics` 객체에서 key만 없으면 0**, key가 있고 count가 `null`이거나 metrics 객체가 없으면 `null`로 처리하도록 비교기를 최소 수정했다. [Grafana k6 dropped iterations 문서](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/)와 [built-in metrics reference](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)가 이 Counter의 의미를 뒷받침한다.

```text
node perf/read/commit-cache/compare_commit_cache_results.mjs \
  --result-root perf/read/commit-cache/results/task10-20260802-c/<formal-group> \
  --blocks <500|1000> --pattern <pattern> --load-profile <profile> \
  --redis-fail-open-passed \
  --output-dir .superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-11-analysis-v2/<group>/...
```

| 비교군 | 조건 수 | 비교기 exit / 판정 | 결정에 쓰는 해석 |
| --- | ---: | --- | --- |
| formal-main | Hot/Mixed × VU 20/50/100 = 6 | 각 0 / `CACHE_CANDIDATE_CONFIRMED` | **핵심 선택 근거** |
| formal-large | Hot/Mixed × VU 50 = 2 | 각 0 / `CACHE_CANDIDATE_CONFIRMED` | 보조 방향성 |
| formal-cold 보정 | Cold × VU 50 = 1 | 0 / `NO_MEANINGFUL_IMPROVEMENT` | cold miss에서는 cache 이점 없음 |
| formal-cold-burst 보정 | VU 10/50/100 = 3 | 각 0 / `INCONCLUSIVE`, `NO_MEANINGFUL_IMPROVEMENT`, `INCONCLUSIVE` | p95 회귀 또는 방향 불일치, 보조 위험 |

core와 large의 v2 결과는 그대로 유지한다. 기존 formal-cold와 formal-cold-burst v2의 Caffeine before snapshot에는 Prometheus `commit_content_assemble_seconds_count`가 없어 네 조건이 `INSUFFICIENT_DATA`였다. timer 사전 등록 뒤 현재 소스로 Caffeine만 같은 seed·부하·run metadata에서 12회 재측정했고, 12개 before에 count 0, after에 유한한 count가 모두 존재했다. v3는 원래 none/Redis와 run 번호로 짝지은 보정 분석이며 Caffeine-only 재측정이므로 provider 교차순서 증거에는 포함하지 않는다.

## 핵심 6조건: p95와 처리량 관측값

아래는 각 provider 3회의 평균이며, 괄호의 p95 변화율은 run 번호를 짝지은 `(none - candidate) / none`의 평균이다. `commit_get_failed`와 cache get/put error는 기록된 모든 core run에서 0이었고, raw key가 없는 dropped는 k6 Counter 의미에 따라 0으로 판정됐다.

| 조건 | none p95 / 처리량 | Caffeine p95 / 처리량 (p95 변화) | Redis p95 / 처리량 (p95 변화) | baseline 변동폭 / 최소 의미 차이 |
| --- | --- | --- | --- | --- |
| Hot VU20 | 28.49 ms / 301.70/s | 22.49 ms / 324.74/s (+21.06%) | 23.64 ms / 325.08/s (+17.05%) | 10.61% / 10.61% |
| Hot VU50 | 157.51 ms / 306.58/s | 141.46 ms / 348.68/s (+10.18%) | 144.32 ms / 331.99/s (+8.33%) | 3.07% / 10.00% |
| Hot VU100 | 378.98 ms / 329.38/s | 336.07 ms / 360.56/s (+11.32%) | 347.86 ms / 355.81/s (+8.21%) | 1.30% / 10.00% |
| Mixed VU20 | 27.42 ms / 314.32/s | 22.07 ms / 344.61/s (+19.50%) | 23.32 ms / 332.27/s (+14.96%) | 3.02% / 10.00% |
| Mixed VU50 | 157.22 ms / 322.75/s | 141.05 ms / 354.09/s (+10.28%) | 140.94 ms / 348.51/s (+10.33%) | 3.36% / 10.00% |
| Mixed VU100 | 404.36 ms / 336.53/s | 349.33 ms / 366.35/s (+13.60%) | 345.83 ms / 354.03/s (+14.47%) | 2.27% / 10.00% |

## 핵심 6조건: 자원과 cache 관측값

수치는 3회 평균 증분이다. `heap Δ`는 전/후 snapshot의 부호 있는 차이이므로 음수는 heap 사용량이 측정 구간 후 더 작았다는 뜻일 뿐, heap 개선의 일반화 근거가 아니다. `GC`는 `count / pause seconds`, `Redis memory`는 `used_memory after - before` bytes다. 존재하지 않는 값은 기록하지 않았다.

| 조건 | none: assemble / hit / heap / GC | Caffeine: assemble / hit / heap / GC | Redis: assemble / hit / heap / GC / memory |
| --- | --- | --- | --- |
| Hot VU20 | 19,194 / 0 / +128,534,469 / 122 / 0.615 | 0 / 20,757 / +23,905,589 / 19 / 0.111 | 0 / 20,722 / +1,627,352 / 67 / 0.327 / +24,995 B |
| Hot VU50 | 19,666 / 0 / +79,982,093 / 110 / 0.678 | 0 / 22,301 / +21,307,443 / 21 / 0.160 | 0 / 21,475 / +108,082,597 / 67 / 0.413 / +38,643 B |
| Hot VU100 | 21,141 / 0 / +181,772,755 / 115 / 0.723 | 0 / 23,147 / −30,216,059 / 21 / 0.149 | 0 / 22,664 / +66,746,539 / 65 / 0.421 / +31,819 B |
| Mixed VU20 | 20,044 / 0 / +62,662,411 / 133 / 0.643 | 0 / 22,031 / +45,842,851 / 19 / 0.110 | 0 / 21,367 / +76,051,179 / 69 / 0.337 / +31,819 B |
| Mixed VU50 | 20,702 / 0 / −51,076,888 / 116 / 0.674 | 0 / 22,775 / −7,773,845 / 21 / 0.136 | 0 / 22,425 / −1,821,595 / 76 / 0.481 / +38,643 B |
| Mixed VU100 | 21,490 / 0 / −36,123,160 / 113 / 0.775 | 0 / 23,526 / +61,885,984 / 15 / 0.118 | 0 / 22,981 / +39,433,083 / 70 / 0.447 / +52,291 B |

핵심 조건에서 cache get/put error Δ는 세 provider 모두 0이다. none의 assemble은 매 run 양수이고 cache hit은 0, Caffeine/Redis는 assemble 0 및 hit 양수로 기록됐다. 따라서 core의 cache 경로 gate는 충족한다.

## 보조 조건

지원되는 formal-large와 보정된 formal-cold/formal-cold-burst도 비교기를 실제 실행했다. large 2개는 기존 v2의 `CACHE_CANDIDATE_CONFIRMED`를 유지한다. cold v3는 `NO_MEANINGFUL_IMPROVEMENT`, cold-burst v3는 VU10/50/100 순서로 `INCONCLUSIVE`/`NO_MEANINGFUL_IMPROVEMENT`/`INCONCLUSIVE`다. p95/처리량 3회 평균은 다음과 같다.

| 조건 | none | Caffeine | Redis | 관측 해석 |
| --- | --- | --- | --- | --- |
| Large Hot VU50 | 194.67 ms / 163.99/s | 170.07 ms / 182.08/s | 173.12 ms / 178.06/s | cache 후보 확인, Caffeine/Redis 차이는 10% 미만 |
| Large Mixed VU50 | 201.53 ms / 165.00/s | 168.00 ms / 180.03/s | 177.14 ms / 175.11/s | cache 후보 확인, Caffeine/Redis 차이는 10% 미만 |
| Cold VU50 | 231.64 ms / 76.06/s | 361.54 ms / 67.23/s | 260.71 ms / 77.86/s | `NO_MEANINGFUL_IMPROVEMENT`; Caffeine assemble 400/400/400 |
| Cold burst VU10 | 37.96 ms / 2.53/s | 83.20 ms / 2.55/s | 46.20 ms / 2.66/s | `INCONCLUSIVE`; Redis p95 방향 불일치 |
| Cold burst VU50 | 149.99 ms / 12.16/s | 208.28 ms / 10.53/s | 224.33 ms / 11.55/s | `NO_MEANINGFUL_IMPROVEMENT` |
| Cold burst VU100 | 271.36 ms / 24.60/s | 280.71 ms / 21.60/s | 291.46 ms / 25.45/s | `INCONCLUSIVE`; 두 cache p95 방향 불일치 |

보정 Caffeine의 assemble Δ는 Cold VU50에서 400/400/400, cold-burst VU10/50/100에서 모두 1/1/1이다. cache hit Δ는 Cold VU50에서 0/0/0, cold-burst VU10에서 0/0/0, VU50에서 40/40/40, VU100에서 90/90/90이다. Cold 계열의 기존 Redis memory Δ는 Cold VU50 +32,852,016/+32,827,328/+32,852,016 B, cold-burst VU10 +106,736/+82,048/+106,736 B, VU50 +127,208/+85,432/+110,056 B, VU100 +127,208/+102,520/+107,176 B다. 이는 단일 실행/구간 증분일 뿐 capacity 산정 자료가 아니다.

### Saturation: hand-derived 탐색 관측 (판정 제외)

formal-saturation은 provider별 run-1 한 번만 승인됐으며, Redis run-2는 부분 artifact라 입력/집계/판정에서 제외했다. 비교기는 3회 입력이 필요하므로 saturation에는 실행하지 않았다. 아래 표는 `summary.json`의 p95·iterations rate·failure rate와 Prometheus/Redis before/after의 차이를 사람이 계산한 것이다.

계산식: `Δmetric = after - before`; Redis memory는 `used_memory(after) - used_memory(before)`. raw source는 `perf/read/commit-cache/results/task10-20260802-c/formal-saturation/<provider>/blocks-500/saturation/rate-25-50-100-200/run-1/` 아래의 `summary.json`, `prometheus-before.txt`, `prometheus-after.txt`(Redis는 `redis-info-*.txt`)다.

| provider | p95 | 처리량 | 실패율 | dropped | assemble Δ / hit Δ | heap Δ | GC count / pause | Redis memory Δ |
| --- | ---: | ---: | ---: | --- | --- | ---: | --- | ---: |
| none | 8.112 ms | 70.710/s | 0 | 미측정 | 17,249 / 0 | +92,432,680 | 265 / 0.497 s | 미측정 |
| Caffeine | 8.618 ms | 70.700/s | 0 | 미측정 | 0 / 17,249 | +17,659,952 | 30 / 0.079 s | 미측정 |
| Redis | 8.393 ms | 70.392/s | 0 | 미측정 | 0 / 17,249 | −3,363,336 | 90 / 0.241 s | +45,160 B |

이 단건은 포화 임계점·provider 우열·오류 시작점을 판정하지 않는다. raw key 부재는 새 comparator 규칙상 0으로 해석될 수 있지만, 3회 입력이 없으므로 saturation은 여전히 비교/선택에서 제외한다. Redis run-2 partial 경로 `.../formal-saturation/redis/.../run-2/`는 이 문서의 모든 표와 analysis 입력에서 제외했다.

## Redis 장애 gate

Task 9의 별도 fail-open/recovery artifact는 **Redis Fail-Open** 안전성 gate를 통과했다.

1. warm 기준: get hit 1,590, miss 82, put success 41, get/put error 0.
2. Redis stop 뒤에도 application health는 `UP`/HTTP 200이었고 verify exit 0, checks 95/95, HTTP failure 0%, deep validation 5종 × 3요청이 통과했다. 동시에 get error는 0→6, put error는 0→3으로 증가하여 장애 관측과 Mongo fallback 응답 성공이 함께 확인됐다.
3. Redis restart/healthy 뒤 첫 verify는 exit 0, checks 95/95이며 miss 82→88, put success 41→44로 재채움이 확인됐다. 두 번째 verify에서 hit 1,590→1,593, miss/put/error 추가 증가는 없어 hit 경로 회복을 확인했다.

출처: `.superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-9-report.md`, raw artifact `.../task-9-smoke-results/redis-fail-open-task9fresh260802b/`. 이 통과는 Redis의 장애 안전성을 지지하지만, Redis가 Caffeine보다 최소 의미 차이 이상 우수하다는 성능 근거는 아니다.

## 최종 선택과 후속 범위

- **채택 후보:** Caffeine. core 6개는 모두 cache 후보를 확인했다. Caffeine과 Redis의 짝지은 p95/처리량 차이는 모든 core 조건에서 해당 최소 의미 차이(10.00% 또는 10.61%) 미만이며, Redis의 heap/GC 완화도 일관되게 우수하지 않다. Hot VU50/VU100에서는 Redis 자체 p95 개선도 최소 의미 차이에 미달했다. 규칙상 동률/최소 의미 차이 미만이면 Caffeine을 선택한다.
- **기각:** Redis의 fail-open 자체는 기각하지 않는다. 다만 Redis가 Caffeine보다 p95/처리량에서 최소 의미 차이 이상 우수하거나 Caffeine의 heap/GC 문제를 그렇게 완화했다는 증거가 없으므로 이 선택에서는 채택하지 않는다.
- **과거 cold 판정 정정:** 기존 cold 수치는 provider별 JVM 재시작 조건이 비대칭이어서 최종 채택 근거에서 제외한다. 대칭 Cold VU50 재측정은 Caffeine p95가 2/3 개선·1/3 악화이고 baseline 변동폭 약 26%를 넘지 못해 `INCONCLUSIVE`다.
- **보류 위험:** cold miss latency와 초기 채움 경로의 성능 이득 또는 회귀는 확인되지 않았다. 운영 hit ratio와 cold p95를 함께 관측해야 한다.
- **Single-Flight 확인:** 현재 어노테이션 구현의 동일 키 Cold Burst VU50에서 요청 50건이 조립 1회, miss 1회, hit 49회로 처리됐다. 이는 부하 증폭 방지 동작의 근거이며 p95 개선 근거는 아니다.
- **최종 구현:** Caffeine만 남기고 Spring Cache 어노테이션 경계로 전환했다. 최종 구현은 `8fab327`, 성능 테스트 도구는 `4f96481`, 결과 문서는 `77eab3c`에 기록되어 있다.
- **운영 다음 단계:** 실제 hit ratio와 cold p95를 Grafana로 관측하고 손해가 확인되면 캐시를 비활성화한다. staging 부하와 saturation은 사용자 지시에 따라 실행하지 않았다.
- **운영 한계:** 실제 운영 트래픽·로그 기반 hit rate/latency/error evidence는 없다. 로컬 단일 app/데이터셋/환경 수치와 운영 절대 수치를 합치지 않는다.

## 생성 artifact

- v1 비교기 출력(보존, key-absence 수정 전): `.superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-11-analysis/`
  - `formal-main/blocks-500/{hot,mixed}/vus-{20,50,100}/comparison.{json,md}`
  - `formal-large/blocks-1000/{hot,mixed}/vus-50/comparison.{json,md}`
  - `formal-cold/blocks-500/cold/vus-50/comparison.{json,md}`
  - `formal-cold-burst/blocks-500/cold_burst/vus-{10,50,100}/comparison.{json,md}`
  - `comparator-exit-status.tsv`
- v2 비교기 출력과 exit 기록: `.superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-11-analysis-v2/`
  - `formal-main/blocks-500/{hot,mixed}/vus-{20,50,100}/comparison.{json,md}`
  - `formal-large/blocks-1000/{hot,mixed}/vus-50/comparison.{json,md}`
  - `formal-cold/blocks-500/cold/vus-50/comparison.{json,md}`
  - `formal-cold-burst/blocks-500/cold_burst/vus-{10,50,100}/comparison.{json,md}`
  - `comparator-exit-status.tsv`
- cold 보정 입력: `.superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-11-analysis-input-v3/`
  - 원래 `formal-cold*`의 none/Redis와 `cold-recheck-20260802-b`의 Caffeine을 provider별로 연결한다.
- v3 cold 보정 출력: `.superpowers/sdd/2026-08-01-commit-content-cache-benchmark-plan/task-11-analysis-v3/`
  - `formal-cold/blocks-500/cold/vus-50/comparison.{json,md}`
  - `formal-cold-burst/blocks-500/cold_burst/vus-{10,50,100}/comparison.{json,md}`
  - `comparator-exit-status.tsv`
