# 커밋 본문 캐시 비교 실험 설계

## 배경

커밋 본문은 변경된 블록만 새로 저장하고 이전 커밋의 블록을 재사용하는 증분 저장 구조를 사용한다. 이 구조는 쓰기 중복과 저장 공간을 줄이지만, 커밋 조회 시에는 분리된 데이터를 다시 조립해야 한다.

현재 커밋 조회 경로는 다음과 같다.

```text
GET /api/document/{docId}/commit/{commitId}
→ MySQL에서 커밋 조회
→ MongoDB에서 CommitBlockSequence 조회
→ MongoDB에서 Block 일괄 조회
→ blockOrders 순서대로 본문 재조립
→ JSON 응답
```

커밋 내용은 생성 후 수정되지 않으므로 캐시 적합성은 높다. 반면 현재 서비스는 단일 애플리케이션 인스턴스로 운영되고 있으며 실제 커밋 재조회 로그가 없다. 따라서 Redis를 미리 해법으로 정하지 않고, 캐시가 실제로 필요한지부터 확인한 뒤 Caffeine과 Redis를 동일 조건에서 비교한다.

## 확인된 사실과 미확정 항목

### 확인된 사실

- 운영 환경은 단일 애플리케이션 인스턴스다.
- 현재 실제 트래픽은 거의 없다.
- 커밋 내용은 생성 후 수정되지 않는다.
- 커밋 본문 조회는 MySQL 1회, MongoDB 2회, 애플리케이션 본문 조립을 수행한다.
- 사용자가 과거 커밋 사이를 오가며 같은 커밋을 반복 조회할 가능성은 있지만 운영 로그 근거는 없다.
- 프로젝트는 인증 코드를 저장하기 위해 Caffeine을 이미 사용한다.
- Redis는 현재 의존성이나 운영 인프라에 포함되어 있지 않다.
- 운영 컨테이너에서 확인한 JVM 최대 heap은 약 11.52GiB이고 무부하 시점 heap 사용량은 약 67.5MiB다.

무부하 heap 사용량은 캐시 적용 후 메모리나 GC 동작을 판단하는 근거로 사용하지 않는다. 실제 부하에서 다시 측정한다.

### 미확정 항목

- 100개, 500개, 1,000개 블록을 가진 커밋의 조회 지연시간
- MongoDB 조회와 본문 조립이 전체 응답시간에서 차지하는 비중
- Cold, Hot, Mixed 접근 패턴별 cache hit 효과
- Caffeine 적용 후 heap과 GC 변화
- Redis 네트워크 및 JSON 직렬화 비용
- 캐시 working set과 항목당 메모리 크기

## 목표

- 블록 수와 접근 집중도에 따라 커밋 조회 비용이 어떻게 변하는지 측정한다.
- 캐시가 없는 구조, Caffeine, Redis를 동일한 캐시 경계와 부하 조건에서 비교한다.
- p95, 처리량, MongoDB 조회 수, heap, GC, Redis 메모리와 장애 동작을 함께 평가한다.
- 반복 측정 변동폭보다 큰 차이만 유효한 개선으로 판단한다.
- 실험 결과로 캐시 미도입, Caffeine, Redis 중 하나를 선택한다.
- 선택 과정과 원본 결과를 재현 가능한 artifact로 남긴다.

## 비목표

- 다중 애플리케이션 인스턴스 환경을 만들지 않는다.
- 미래의 분산 환경을 Redis 도입 근거로 사용하지 않는다.
- 기존 커밋 조회 권한 정책을 변경하지 않는다.
- 커밋 생성 경로의 알고리즘 복잡도를 개선하지 않는다.
- 커밋 저장 모델이나 MongoDB collection 구조를 변경하지 않는다.
- 문서 그래프 Read Model을 변경하지 않는다.
- HTTP ETag 또는 브라우저 캐시를 첫 실험에 포함하지 않는다.
- 5,000개 블록 한계 실험을 기본 범위에 포함하지 않는다.
- 캐시 없음, Caffeine, Redis를 영구적으로 전환하는 범용 운영 구조를 만들지 않는다.

## 작업 분리

이번 설계는 다음 첫 번째 작업만 다룬다.

```text
캐시 타당성 baseline
→ Caffeine 후보 비교
→ Redis 후보 비교
→ 결과 분석과 최종 후보 선택
```

실험 전에는 실제 재조회율, 항목 크기, 적정 TTL과 메모리 예산을 알 수 없다. 따라서 최종 운영 적용은 측정 결과가 유효할 때 별도 `commit-content-cache-adoption-design`으로 설계한다. 후속 설계에서는 선택된 후보 하나의 TTL, 용량, eviction, 운영 장애 대응과 staging 적용을 확정한다.

이번 작업은 여러 파일과 Redis 실험 인프라, 성능 주장에 영향을 주므로 구현 전에 기존 GitHub Issue를 확인하고 없으면 저장소 템플릿에 맞는 Issue로 범위와 측정 조건을 합의한다. Issue 생성과 assignee 지정은 요청자의 GitHub 계정을 확인한 뒤 별도 승인을 받아 수행한다.

## 검토한 접근법

### 조립된 커밋 본문 Cache-Aside

`CommitContentAssembler.assemble(commitMongoId)`가 만드는 조립 완료 본문을 캐시한다.

장점:

- 제거하려는 MongoDB 조회와 본문 조립 비용에 경계가 정확히 맞는다.
- 기존 API와 MySQL 커밋 확인 흐름을 유지한다.
- Caffeine과 Redis를 같은 메서드 경계에서 비교할 수 있다.
- 불변 커밋이므로 update invalidation이 없다.

단점:

- cache miss는 기존 경로와 같은 비용을 부담한다.
- 큰 본문은 Caffeine heap 또는 Redis 메모리를 점유한다.
- cache hit에서도 MySQL 커밋 조회는 남는다.

이 접근법을 선택한다.

### CommitResponse 전체 캐시

서비스 또는 API 응답 전체를 `docId + commitId`로 캐시하는 방식이다. 구현은 단순하지만 API DTO, 요청 문맥과 캐시가 결합되고 사용자별 중복 key가 생길 수 있다. 해결하려는 MongoDB 조립 비용보다 넓은 경계를 캐시하므로 선택하지 않는다.

### 완성 본문 Snapshot 저장

커밋 생성 시 조회용 완성 본문을 별도로 저장하면 cold 조회도 줄일 수 있다. 그러나 증분 저장으로 줄인 저장 중복을 다시 만들고, 커밋 생성과 이종 DB 보상 범위를 넓힌다. cache miss까지 개선해야 한다는 측정 근거가 생길 때의 후속 대안으로 남긴다.

## 선택한 아키텍처

### 데이터 흐름

```text
CommitController
→ CommitService.getCommit(docId, commitId, userId)
→ 기존 문서 확인
→ 기존 MySQL Commit 조회
→ commitMongoId로 commitContentCache 조회
   ├─ hit: 조립된 본문 반환
   └─ miss:
       → CommitContentAssembler 실행
       → MongoDB CBS 조회
       → MongoDB Block 일괄 조회
       → 본문 조립
       → 캐시 저장
→ 기존 CommitResponse 반환
```

### 캐시 key와 value

```text
cache name = commitContentCache
key        = commitMongoId
value      = List<Map<String, Object>> 형태의 조립 완료 본문
```

`commitMongoId`는 실제 MongoDB 커밋 스냅샷을 직접 식별하며 사용자별 데이터가 아니다. `CommitResponse` 전체 대신 본문을 저장해 API 응답 형식과 캐시 데이터를 분리한다.

### 책임 유지

- `CommitService`는 기존 문서 확인, MySQL 커밋 조회와 응답 매핑을 유지한다.
- `CommitContentAssembler`는 MongoDB 원본 조회, 순서 복원과 데이터 누락 예외 처리를 유지한다.
- Spring Cache 추상화를 사용해 후보별 CacheManager만 바꾼다.
- 기존 인증 코드 캐시는 계속 Caffeine을 사용한다.
- Redis 후보에서도 커밋 본문 캐시만 별도 CacheManager를 사용한다.

실험이 끝나면 패배한 provider 설정과 전환용 설정을 제거한다. 캐시가 필요 없으면 커밋 본문 캐시 코드 전체를 제거한다.

## 캐시 동작 정책

### Cache hit

MySQL에서 커밋과 `commitMongoId`를 확인한 뒤 캐시 본문을 반환한다. MySQL 확인을 캐시보다 앞에 유지해 삭제된 커밋이 캐시만으로 반환되지 않게 한다.

### Cache miss

MongoDB CBS와 Block을 조회하고 본문을 조립한 뒤 캐시에 저장한다. 캐시 저장에 실패하더라도 이미 조립한 본문은 정상 반환한다.

### 캐시하지 않는 결과

- 존재하지 않는 CBS
- 일부 Block이 누락된 불완전한 본문
- MongoDB 조회 예외
- 조립 과정의 예외
- null 결과

### 동시 Cache miss

단일 애플리케이션 안에서 같은 `commitMongoId`에 대한 로딩만 single-flight로 묶는다. 첫 요청이 MongoDB에서 조립하는 동안 같은 key의 다른 요청은 그 결과를 기다린다. 전체 캐시를 잠그지 않으며 Redis 분산 락은 사용하지 않는다.

### 삭제

정상 단일 커밋 삭제 경로에서는 해당 `commitMongoId`를 best-effort로 제거한다. 캐시 제거 실패는 삭제 트랜잭션을 실패시키지 않는다. 커밋 메타데이터가 먼저 조회되므로 삭제된 본문이 캐시에 남아 있어도 API에서 접근할 수 없다.

문서와 브랜치의 다건 삭제 경로에 캐시 제거 의존성을 퍼뜨리지 않는다. 실험용 TTL과 bounded capacity로 고아 항목을 정리하고, 선택된 provider의 전체 삭제 정리는 후속 운영 적용 설계에서 다룬다.

### 실험용 TTL과 용량

주 비교에서 eviction이 hit 비용을 방해하지 않도록 두 후보 모두 다음 논리 조건을 사용한다.

- TTL: 저장 후 10분
- 대상 key: 최대 400개
- Caffeine: 최대 400개 항목이 들어가도록 설정
- Redis: 실험 전용 인스턴스와 namespace에 400개 항목을 저장하며 주 비교 중 maxmemory eviction을 발생시키지 않음

이 값은 운영 정책이 아니라 60초 측정과 warm-up 동안 캐시가 만료되거나 축출되지 않게 하기 위한 실험 통제값이다.

## 장애 처리

캐시는 보조 계층이고 MongoDB가 원본이므로 Fail-Open을 적용한다.

```text
캐시 정상 → 캐시 사용
캐시 조회 오류 → MongoDB 원본 조회와 본문 조립
캐시 저장 오류 → 조립 본문 정상 반환
MongoDB 원본 오류 → 기존 API 오류 반환
```

Redis 연결 실패, timeout 또는 역직렬화 실패는 캐시 오류 metric에 기록하고 원본 조회로 대체한다. 이는 별도 서버로 트래픽을 전환한다는 의미가 아니라, 해당 요청이 기존 MongoDB 조립 경로를 실행한다는 의미다.

Redis 장애 실험은 다음 순서로 수행한다.

1. Redis 정상 상태에서 warm 조회를 확인한다.
2. 부하 도중 실험용 Redis를 중지한다.
3. 동일 요청이 MongoDB 원본 경로로 성공하는지 확인한다.
4. cache error, MongoDB 조회 증가와 p95·p99 변화를 기록한다.
5. Redis를 복구한 뒤 첫 miss에서 다시 저장되고 이후 hit가 발생하는지 확인한다.

첫 비교 실험에서는 circuit breaker나 재시도 라이브러리를 추가하지 않는다. Redis가 최종 후보로 선택됐는데 반복 timeout 문제가 관찰되면 후속 운영 적용 설계에서 다룬다.

## 관측 지표

Provider와 무관하게 다음 지표를 사용한다.

```text
commit_content_cache_get_total{result=hit|miss|error}
commit_content_cache_put_total{result=success|error}
commit_content_cache_eviction_total
commit_content_assemble_seconds
```

JVM 및 컨테이너 지표:

- `jvm_memory_used_bytes`
- `jvm_memory_committed_bytes`
- `jvm_gc_pause_seconds_count`
- `jvm_gc_pause_seconds_sum`
- 컨테이너 CPU와 메모리

k6 지표:

- `op_commit_get_ms`
- `op_commit_get_payload_bytes`
- `commit_get_failed`
- `http_req_duration`
- `dropped_iterations`
- 처리량

로그와 metric label에는 커밋 본문, Block 내용, 사용자 개인정보, `commitMongoId`를 넣지 않는다. provider, 캐시 동작과 예외 종류만 저카디널리티 값으로 기록한다.

## API 기반 성능 데이터 생성

직접 DB에 데이터를 넣는 `PerfDataInitializer` 대신 `perf/seed/seed_dataset.js`가 실제 문서 및 커밋 생성 API를 호출하도록 사용한다.

기존 seed 스크립트는 커밋마다 전체 블록을 새로 생성하므로 다음 옵션을 추가한다.

```text
COMMIT_BLOCK_CHANGE_RATE=0.1
```

동작 규칙:

- 첫 커밋은 전체 블록을 생성한다.
- 이후 커밋은 전체 블록의 10%만 새로 생성한다.
- 나머지 90%는 이전 커밋의 editor block id를 `blockOrders`에서 재사용한다.
- 모든 커밋의 `blockOrders` 길이는 `BLOCKS_PER_COMMIT`과 같다.
- 변경 구간은 커밋 번호에 따라 회전시켜 실행마다 같은 데이터를 만든다.
- 기본값은 `1.0`으로 두어 기존 전체 변경 동작을 유지한다.
- 커밋 조회 전용 데이터셋은 `FEATURE_BRANCHES=0`으로 생성한다.

Seed 실행시간과 실패율은 별도 결과로 저장하고 조회 성능 지표에는 포함하지 않는다. Seed 이후 애플리케이션을 재시작한 뒤 조회 실험을 시작한다.

## 데이터셋

기본 데이터셋:

```text
사용자              20명
사용자당 문서        2개
문서당 main 커밋    10개
전체 커밋           400개
커밋별 변경률       10%
Feature branch       0개
```

블록 크기:

```text
100 / 500 / 1,000
```

대조 실험에서는 `COMMIT_BLOCK_CHANGE_RATE=1.0`을 사용해 전체 블록이 바뀌는 데이터셋을 한 번 측정한다. 이를 실제 사용자 패턴이라고 주장하지 않고 MongoDB working set이 큰 대조군으로만 사용한다.

## 부하 시나리오

### 블록 크기 확장

캐시 없는 baseline에서 실행한다.

```text
100 / 500 / 1,000블록
VU 20
60초
각 조건 3회
```

블록 수에 따른 p95, payload, MongoDB 조회시간, 조립시간, heap과 GC 변화를 확인한다.

### Cold

400개 커밋을 각각 한 번씩 조회한다.

```text
캐시 없음 / Caffeine / Redis
VU 50
각 후보 3회
```

cache miss, 원본 조립과 cache write 비용을 비교한다. Cold p99는 표본 수가 작으므로 참고값으로만 사용하고 p95를 주 지표로 사용한다.

### Hot

각 사용자에게 대표 커밋 2개를 배정해 전체 40개 hot key를 반복 조회한다. 한 개 key만 반복하는 비현실적인 부하는 사용하지 않는다.

### Mixed

요청 80%는 각 사용자의 상위 20% 커밋에, 나머지 20%는 그 외 커밋에 분배한다. 실제 운영 분포가 아니라 캐시 집중도 민감도 실험으로 명시한다.

Hot과 Mixed의 주 비교 조건:

```text
500블록
캐시 없음 / Caffeine / Redis
VU 20 / 50 / 100
각 60초
각 조건 3회
```

큰 본문 조건:

```text
1,000블록
캐시 없음 / Caffeine / Redis
VU 50
Hot / Mixed
각 60초
각 조건 3회
```

### Cold burst

빈 캐시에서 같은 커밋에 동시에 요청한다.

```text
VU 10 / 50 / 100
```

MongoDB 조립 횟수, single-flight 동작, 첫 응답 완료 전 대기 지연과 로딩 실패 전파를 확인한다.

### 포화점

Mixed 500블록 조건에서 `ramping-arrival-rate`를 사용한다.

```text
25 req/s  — 60초
50 req/s  — 60초
100 req/s — 60초
200 req/s — 60초
```

목표 요청률 유지 여부, `dropped_iterations`, p95·p99 급증 구간, 오류 시작 구간, MongoDB 호출량, CPU, heap, GC와 Redis 메모리를 확인한다. 200 req/s를 넘는 후속 증가는 이 설계 범위에 포함하지 않는다.

## 비교 공정성

세 후보에서 다음을 동일하게 유지한다.

- 데이터셋과 요청 순서
- 애플리케이션 JVM 설정
- MongoDB 위치
- 캐시 key와 value
- 실험용 TTL과 논리 key 수
- VU, 요청률, warm-up, 측정 시간
- 애플리케이션 시작 후 준비 시간
- k6 실행 호스트

실행 순서가 JIT와 MongoDB warm-up의 이점을 한 후보에 몰아주지 않도록 교차한다.

```text
1회: 캐시 없음 → Caffeine → Redis
2회: Redis → 캐시 없음 → Caffeine
3회: Caffeine → Redis → 캐시 없음
```

각 후보 시작 전에 애플리케이션을 재시작한다. Caffeine은 앱 재시작으로 비우고 Redis는 실험 전용 컨테이너 또는 namespace만 초기화한다. 공유 Redis의 전체 DB를 삭제하지 않는다.

로그인, 문서 탐색, graph 기반 commit id 수집, seed와 warm-up은 `op_commit_get_ms`에서 제외한다. 강한 부하 구간에서는 매 요청마다 깊은 JSON 비교를 하지 않고 status와 응답 크기를 확인한다. 별도 사전 검증에서 Block 수와 대표 내용을 확인한다.

## 결과 판정

### 주 지표

1. p95
2. 처리량
3. MongoDB 조회 횟수
4. 오류율

### 보조 지표

- p99
- cold miss 회귀
- heap 증가량
- GC 횟수와 pause
- Redis 메모리
- 응답 payload

3회 반복의 실행 간 변동폭은 다음으로 계산한다.

```text
반복 변동폭 = (최댓값 - 최솟값) / 3회 평균 × 100
최소 의미 차이 = max(10%, baseline 반복 변동폭)
```

성능 개선은 다음을 모두 만족해야 한다.

- 3회 모두 같은 방향의 결과가 나온다.
- p95 개선 폭이 최소 의미 차이보다 크다.
- cache hit에서 MongoDB 조회가 실제로 감소한다.
- cold path 회귀와 자원 비용을 함께 공개한다.
- p99 하나만 좋아진 결과로 개선을 주장하지 않는다.

최종 선택 규칙:

- cache hit 효과가 최소 의미 차이보다 작으면 캐시를 도입하지 않는다.
- 캐시 없는 구조보다 유효하게 개선된 후보끼리 p95, 처리량, MongoDB 조회 감소, heap과 GC를 순서대로 비교한다.
- Redis는 Caffeine보다 p95 또는 처리량이 최소 의미 차이 이상 우수하거나, Caffeine에서 관찰된 heap 또는 GC 문제를 최소 의미 차이 이상 줄이고 Fail-Open 검증을 통과할 때 선택한다.
- Caffeine과 Redis의 차이가 최소 의미 차이보다 작으면 운영이 단순한 Caffeine을 선택한다.
- Redis 장애가 정상 조회를 실패시키면 Redis를 제외한다.

## 자동화 테스트

### Seed 검증

- 기본 변경률 `1.0`에서 기존 전체 Block 생성 동작을 유지한다.
- 변경률 `0.1`에서 첫 커밋만 전체 Block을 생성한다.
- 이후 커밋은 정확히 10%의 신규 Block을 생성한다.
- 모든 커밋의 `blockOrders` 길이는 전체 블록 수와 같다.
- 첫 번째, 중간, 마지막 커밋 조회가 전체 본문을 반환한다.
- 생성된 문서와 커밋 수가 설정과 일치한다.

### 공통 캐시 동작

- 첫 조회는 miss 후 MongoDB에서 조립한다.
- 같은 key의 두 번째 조회는 hit이며 MongoDB를 다시 조회하지 않는다.
- 서로 다른 `commitMongoId`는 별도 항목이다.
- 원본 조회와 조립 예외를 캐시하지 않는다.
- 캐시 저장 실패가 정상 응답을 실패시키지 않는다.
- 캐시 조회 실패 시 원본 조회로 대체한다.
- 같은 key의 동시 요청은 한 번만 조립한다.
- 커밋 삭제 후 캐시 본문이 API에서 반환되지 않는다.
- 캐시 적용 전후 응답을 깊은 비교했을 때 동일하다.

### Redis 실제 연동

Mock만 사용하지 않고 테스트용 실제 Redis 컨테이너에서 확인한다.

- 중첩 Map과 List의 JSON 직렬화 및 역직렬화
- key namespace 분리
- TTL 적용
- Redis 중지 중 원본 조회 성공
- Redis 복구 후 cache miss, 저장, 이후 hit
- 기존 인증용 Caffeine 캐시와의 격리

### 회귀 테스트

- 기존 커밋 생성 통합 테스트
- 기존 커밋 조회 테스트
- 기존 커밋 삭제 테스트
- `CommitContentAssembler` 테스트
- 인증 코드 Caffeine 테스트
- 전체 Gradle 테스트

## 결과 보존

각 실행에서 다음을 보존한다.

- 원본 k6 JSON
- 실행 환경과 Git revision
- 데이터셋 조건과 Block 변경률
- JVM 및 캐시 설정
- MongoDB 위치
- CPU, heap, GC snapshot
- Redis 메모리
- 3회 비교 요약

저장 경로는 `perf/read/results/commit-cache/` 아래에서 provider, block 수, 접근 패턴과 반복 번호를 구분한다. 3회 반복을 완료하지 않은 결과는 포트폴리오 성능 주장에 사용하지 않는다.

## Staging 검증

로컬에서 선택된 후보 하나만 staging에서 다음 조건으로 3회 확인한다.

```text
500블록
Mixed
VU 50
60초
```

Staging에서는 포화 테스트를 실행하지 않는다. 성능 테스트 계정과 `RUN_ID`로 운영 데이터와 분리하며 오류율이 상승하면 중단한다. 로컬과 staging 절대값을 합치지 않고 개선 방향이 같은지만 확인한다.

## API, DB, 인프라 영향

- 커밋 조회 API 경로, 요청과 응답 JSON은 변경하지 않는다.
- MySQL 스키마를 변경하지 않는다.
- MongoDB collection 구조를 변경하지 않는다.
- Redis 후보 실험에서만 Redis 의존성과 실험용 컨테이너를 추가한다.
- Redis 후보는 기존 인증 Caffeine CacheManager와 분리한다.
- 패배한 provider와 실험 전환 설정은 결과 선택 후 제거한다.

## 리스크와 제한

- 실제 재조회 로그가 없으므로 Hot과 Mixed 결과는 민감도 실험이며 운영 hit rate로 주장할 수 없다.
- API seed는 실제 생성 경로를 검증하지만 1,000블록 데이터 생성 시간이 길 수 있다. Seed 시간은 조회 지표에서 분리한다.
- 큰 응답에서는 서버 캐시보다 JSON 직렬화와 네트워크가 주 병목일 수 있다. 이 경우 서버 캐시를 확대하지 않고 HTTP 캐시를 후속 대안으로 검토한다.
- Redis는 단일 인스턴스에서 Caffeine보다 느릴 가능성이 높다. 사용 경험을 위해 최종 채택하지 않고 결과 기준을 따른다.
- Caffeine과 Redis의 실제 메모리 표현이 달라 첫 실험에서 동일 byte 예산의 churn 비교를 할 수 없다. 항목당 메모리를 측정한 뒤 후속 운영 적용 설계에서 eviction과 churn을 검증한다.
- 운영 JVM의 무부하 heap 수치는 캐시 메모리 판단 근거가 아니다. 부하 중 지표를 사용한다.

## 완료 기준

- API 기반 seed가 10% 증분 변경 데이터와 기존 100% 변경 데이터를 결정적으로 생성한다.
- 100개, 500개, 1,000개 블록 baseline을 각 3회 측정한다.
- 캐시 없음, Caffeine, Redis를 Cold, Hot, Mixed와 Cold burst 조건에서 비교한다.
- 25, 50, 100, 200 req/s 포화점 실험을 완료한다.
- Redis 장애 시 MongoDB 원본 조회로 정상 응답하는지 검증한다.
- 기능 및 전체 회귀 테스트가 통과한다.
- 원본 결과, 환경, 설정과 3회 요약을 보존한다.
- 결과 판정 규칙으로 캐시 미도입, Caffeine, Redis 중 하나를 선택한다.
- 최종 운영 적용이 필요하면 별도 설계로 전환한다.
- AI는 commit, push, merge를 수행하지 않는다.
