# Document List Performance Benchmark

목표:

- 문서 목록 조회 경로의 읽기 성능을 별도로 측정
- `sidebar`(최근 활동만)와 `full list`(preview 포함), `search`(preview 포함)를 분리 비교
- 문서 수, branch 수, preview 조립 비용이 응답 시간에 미치는 영향을 확인

## Files

- `perf/seed/seed_dataset.js`
  - 목록 테스트에 사용할 문서/브랜치/커밋 데이터를 생성
- `perf/read/doc_list_benchmark.js`
  - `sidebar`, `full list`, `search` 읽기 성능 측정
- `perf/read/doc_graph_benchmark.js`
  - 현재 MySQL projection 기반 graph 조회 성능 측정

## Why this benchmark

현재 목록 조회는 `Doc` 페이지 조회 후, 각 문서마다 최근 branch를 찾고 preview를 조립합니다.
즉 `sidebar`는 JPA 연관 로딩 비용, `full list`와 `search`는 여기에 Mongo preview 조립 비용까지 추가로 확인할 수 있습니다.

## 1) Prepare users

유저 생성은 애플리케이션의 `PerfDataInitializer`를 사용합니다.
API seed 스크립트로 문서를 만들 때는 여기서는 유저만 준비합니다.

```bash
PERF_SEED_USER_COUNT=50
PERF_SEED_DOCS_PER_USER=0
```

유저 패턴:

- `perfuser_u001@test.com` ~ `perfuser_u050@test.com`
- 비밀번호: `Testtest1`

## 2) Seed dataset

공용 seed 스크립트를 사용합니다.
중요한 건 목록 조회 시 문서별 branch/commit이 충분히 많아지도록 데이터를 만드는 것입니다.

```bash
RUN_ID=read01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=50 DOCS_PER_USER=5 \
MAIN_COMMITS=8 FEATURE_BRANCHES=1 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=100 \
SEED_VUS=20 \
k6 run perf/seed/seed_dataset.js
```

애플리케이션 initializer로 DB에 직접 주입할 수도 있습니다. 이 방식은 API 호출 비용 없이
문서, 브랜치, 커밋, 간선, save, Mongo block/commitBlockSequence/saveContent를 생성합니다.

```bash
PERF_SEED_RUN_ID=read01 \
PERF_SEED_USER_COUNT=50 \
PERF_SEED_DOCS_PER_USER=5 \
PERF_SEED_BRANCHES_PER_DOC=2 \
PERF_SEED_COMMITS_PER_BRANCH=8 \
PERF_SEED_BLOCKS_PER_SAVE=100 \
PERF_SEED_BLOCKS_PER_COMMIT=100
```

권장 기준:

- `DOCS_PER_USER`: 5 이상
- `MAIN_COMMITS`: 8 이상
- `FEATURE_BRANCHES`: 1 이상. graph branch 수 확장 테스트에서는 이 값을 늘림
- `FEATURE_COMMITS`: 5 이상
- `BLOCKS_PER_COMMIT`: 100 이상

## 3) Run list benchmark

```bash
RUN_ID=read01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=50 DOCS_PER_USER=5 PAGE_SIZE=10 \
SEARCH_PREFIX=PDEL \
SIDEBAR_VUS=10 FULL_LIST_VUS=10 SEARCH_VUS=10 \
k6 run perf/read/doc_list_benchmark.js
```

`PerfDataInitializer`로 직접 주입한 데이터는 문서 제목 prefix가 `PERF`이므로 검색 벤치에서
아래처럼 맞춥니다.

```bash
SEARCH_PREFIX=PERF
```

결과 파일:

- `perf/read/results/<test-name>/.../*.json`

핵심 지표:

- `op_doc_sidebar_ms`
- `op_doc_list_ms`
- `op_doc_search_ms`
- `http_req_duration`
- `http_req_failed`

## 4) Recommended comparison order

1. `sidebar` vs `full list`
2. `full list` vs `search`
3. 같은 조건에서 `PAGE_SIZE=10`, `20`, `50` 비교

해석 기준:

- `sidebar`보다 `full list`가 크게 느리면 preview 조립 비용 영향이 큼
- `search`까지 더 느리면 목록 조립 비용 + 검색 쿼리 비용이 함께 작동
- `PAGE_SIZE` 증가에 따라 p95가 급격히 오르면 per-doc 조립 비용이 병목일 가능성이 큼

## 5) Run graph benchmark

그래프 CQRS 적용 여부를 판단할 때는 먼저 현재 구조의 baseline을 측정합니다.
Benchmark setup 단계에서 유저별 `/api/document` 목록을 조회해 테스트 대상 `docId`를 준비하고,
실제 부하 구간에서는 `/api/document/{docId}/graph`만 반복 호출합니다.

```bash
RUN_ID=graph01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=50 DOCS_PER_USER=5 \
TITLE_PREFIX=PDEL \
GRAPH_VUS=10 GRAPH_DURATION=30s \
k6 run perf/read/doc_graph_benchmark.js
```

Branch 수 확장용 seed 예시:

```bash
RUN_ID=graph-branch-r1 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=10 DOCS_PER_USER=2 \
MAIN_COMMITS=30 FEATURE_BRANCHES=10 FEATURE_COMMITS=10 BLOCKS_PER_COMMIT=1 \
SEED_VUS=2 \
k6 run perf/seed/seed_dataset.js
```

핵심 지표:

- `op_doc_graph_ms`
- `op_doc_graph_payload_bytes`
- `op_doc_graph_branch_count`
- `op_doc_graph_commit_count`
- `op_doc_graph_edge_count`

해석 기준:

- commit/edge 수 증가에 따라 `op_doc_graph_ms` p95/p99가 뚜렷하게 증가하면 graph read model 후보로 본다.
- 응답 시간이 안정적인데 payload만 커진다면 CQRS보다 응답 축약, pagination, lazy loading을 먼저 검토한다.
- commit 생성 직후 graph 즉시성이 중요하면 read model 조회 전환 시 MySQL fallback 또는 command 응답 보강이 필요하다.

## 커밋 본문 캐시 비교

이 실험은 단일 애플리케이션 인스턴스에서 `none`, `caffeine`, `redis` provider의 커밋 본문 조회 성능을 비교한다. 로컬 앱은 `infra/docker-compose.local.yml`의 `app` 서비스로 빌드하며, provider가 바뀔 때마다 컨테이너를 재생성한다. 이때 MySQL schema와 MongoDB 데이터는 지우지 않으므로 동일한 seed를 세 후보가 공유한다.

### 사전 조건과 Compose 확인

- Docker daemon과 k6가 실행 가능해야 한다.
- `perfuser_u001@test.com`부터 `perfuser_u020@test.com`까지의 성능 테스트 계정이 로컬 DB에 있어야 한다.
- 비밀번호는 shell의 `USER_PASSWORD`에 설정하되 명령이나 결과 파일에 값을 직접 적지 않는다.
- 로컬 health와 Prometheus endpoint는 각각 `http://localhost:9091/actuator/health`, `http://localhost:9091/actuator/prometheus`이다.

```bash
: "${USER_PASSWORD:?USER_PASSWORD를 먼저 설정하세요}"

docker compose -f infra/docker-compose.local.yml config
docker compose -f infra/docker-compose.local.yml --profile commit-cache config
```

기본 Compose config에는 `app`이 포함되고 profile 전용 `redis`는 제외된다. `commit-cache` profile을 지정한 config에는 둘 다 포함된다. 앱은 Redis에 의존하지 않으므로 `none`과 `caffeine` 조건에서는 Redis가 중지되어 있어도 기동해야 한다.

### 400 commit 데이터셋 생성

Block 수마다 서로 다른 `RUN_ID`를 사용해 사용자 20명 × 문서 2개 × main commit 10개, 총 400 commit을 만든다. Feature branch는 만들지 않고, 두 번째 commit부터 Block의 10%만 변경한다. 먼저 캐시 없는 앱을 기동한 뒤 seed를 실행한다.

```bash
COMMIT_CONTENT_CACHE_PROVIDER=none \
docker compose -f infra/docker-compose.local.yml up -d --build --force-recreate app

for attempt in $(seq 1 60); do
  curl -fsS http://localhost:9091/actuator/health | grep -q '"UP"' && break
  [[ "$attempt" == 60 ]] && { echo '앱 health 확인 시간 초과' >&2; exit 1; }
  sleep 2
done

for blocks in 100 500 1000; do
  RUN_ID="commit-cache-b${blocks}" \
  BASE_URL=http://localhost:8080 \
  USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD="$USER_PASSWORD" \
  USER_COUNT=20 DOCS_PER_USER=2 \
  MAIN_COMMITS=10 FEATURE_BRANCHES=0 FEATURE_COMMITS=0 \
  BLOCKS_PER_COMMIT="$blocks" COMMIT_BLOCK_CHANGE_RATE=0.1 \
  SEED_VUS=5 \
  k6 run perf/seed/seed_dataset.js
done
```

Seed 성공 여부와 실행시간은 조회 성능 결과와 분리해 보관한다. 같은 `RUN_ID`로 seed를 반복하면 제목 prefix가 겹칠 수 있으므로 기존 데이터가 남아 있는 상태에서 다시 실행하지 않는다.

### 한 조건 실행

`run_commit_cache_matrix.sh`는 provider에 맞춰 앱을 재생성하고 bounded health check, 응답 사전 검증, warm-up, 본 측정, 관측 snapshot 저장을 순서대로 수행한다. 기존 결과 디렉터리가 있으면 stale summary 혼입을 막기 위해 실행을 거부하므로 재실행 전 경로를 직접 확인하고 별도 위치로 옮겨야 한다.

```bash
PROVIDER=none \
BLOCKS_PER_COMMIT=100 \
PATTERN=hot \
RUN_NO=1 \
RUN_ID=commit-cache-b100 \
BASE_URL=http://localhost:8080 \
USER_PASSWORD="$USER_PASSWORD" \
HOT_VUS=20 HOT_DURATION=60s \
bash perf/read/run_commit_cache_matrix.sh
```

`cold`, `cold_burst`는 사전 검증 이후 Redis namespace를 비우거나 Caffeine 앱을 build 없이 재생성한 뒤 warm-up 없이 측정한다. `none`은 저장 상태가 없으므로 추가 초기화가 필요 없다. `hot`, `mixed`는 별도 10초 warm-up 결과를 임시 디렉터리에 써서 본 측정 `summary.json`을 덮지 않는다. k6 setup의 로그인·대상 탐색·warm-up 요청에는 `op=commit_get` 측정 tag가 붙지 않는다.

정확한 before/after 경계를 위해 runner는 `measurement_gate.mjs`를 `127.0.0.1`에만 열고 port는 OS에 동적으로 할당받는다. 각 실행은 고유 임시 ready file에서 자기 gate 자식이 기록한 실제 port를 확인하므로, 남아 있거나 병렬 실행 중인 다른 gate를 준비 완료로 오인하지 않는다. 본 k6 setup이 로그인·graph 탐색과 provider별 warm-up을 끝낸 뒤 gate에 준비 완료를 알리고 대기한다. runner는 이때 before snapshot을 수집하고 gate를 해제하며, 실제 scenario 요청은 해제 이후에만 시작한다. gate 자식, ready file, setup 대기, app/Redis health 확인은 모두 제한된 수명이나 횟수 안에서만 기다리며 오류 시 gate와 k6 프로세스 및 임시 state directory를 정리한다.

결과는 다음 경로에 저장된다.

```text
perf/read/results/commit-cache/<provider>/blocks-<count>/<pattern>/<load-profile>/run-<1|2|3>/
```

`load-profile`은 VU 기반 시나리오의 `vus-20`, `vus-50`, `vus-100` 또는 포화 시나리오의 `rate-25-50-100-200`처럼 실제 부하를 나타낸다. 따라서 같은 provider·block·pattern에서도 VU 20과 VU 50 결과가 충돌하지 않는다. 각 디렉터리에는 `summary.json`, `run.log`, Git revision, `load_profile`을 포함한 실행 환경의 비민감 설정, 실행 전후 Prometheus·container stats가 들어간다. Redis 조건에는 `redis-info-before.txt`, `redis-info-after.txt`도 추가된다. 비밀번호, Mongo URI, mail 인증 정보, session cookie 값은 기록하지 않는다.

같은 block·pattern·load profile의 provider별 3회 결과는 다음처럼 비교한다.

```bash
node perf/read/compare_commit_cache_results.mjs \
  --result-root perf/read/results/commit-cache \
  --blocks 500 --pattern hot --load-profile vus-20 \
  --redis-fail-open-passed
```

비교 결과는 기본적으로 `comparisons/blocks-500/hot/vus-20/` 아래에 JSON과 한국어 Markdown으로 저장된다. Redis Fail-Open 검증을 수행하지 않았다면 flag를 빼며, 이 경우 Redis는 긍정 후보가 될 수 없다.

### provider 교차 순서와 100/500/1000 Block baseline

JIT와 MongoDB warm-up 이점이 한 provider에 몰리지 않도록 반복별 순서를 교차한다.

```text
1회: none → caffeine → redis
2회: redis → none → caffeine
3회: caffeine → redis → none
```

아래 명령은 Hot VU 20 baseline을 교차 순서로 3회 수행한다. 같은 방식으로 `PATTERN=mixed`를 실행하고, 500 Block 주 비교에서는 `HOT_VUS` 또는 `MIXED_VUS`를 20, 50, 100으로 각각 고정해 비교한다.

```bash
run_hot() {
  local provider="$1" blocks="$2" run_no="$3"
  PROVIDER="$provider" BLOCKS_PER_COMMIT="$blocks" PATTERN=hot RUN_NO="$run_no" \
  RUN_ID="commit-cache-b${blocks}" BASE_URL=http://localhost:8080 \
  USER_PASSWORD="$USER_PASSWORD" HOT_VUS=20 HOT_DURATION=60s \
  bash perf/read/run_commit_cache_matrix.sh
}

for blocks in 100 500 1000; do
  run_hot none "$blocks" 1
  run_hot caffeine "$blocks" 1
  run_hot redis "$blocks" 1

  run_hot redis "$blocks" 2
  run_hot none "$blocks" 2
  run_hot caffeine "$blocks" 2

  run_hot caffeine "$blocks" 3
  run_hot redis "$blocks" 3
  run_hot none "$blocks" 3
done
```

Cold burst는 한 key에 동시에 접근하는 VU를 10, 50, 100으로 바꿔 single-flight와 Redis 왕복 비용을 확인한다. 포화 시나리오는 25→50→100→200 req/s까지만 실행한다.

### Redis 중지와 복구 확인

Redis provider 앱을 한 번 기동한 뒤 Redis만 중지한 상태에서 임시 결과 경로로 `verify`를 실행하면 fail-open fallback을 확인할 수 있다. Redis를 다시 시작하고 health가 복구된 뒤 같은 검증을 반복한다. 이 절차는 정식 3회 비교 결과 경로를 사용하지 않는다.

```bash
docker stop docsa-redis-local

RUN_ID=commit-cache-b500 BASE_URL=http://localhost:8080 \
USER_PASSWORD="$USER_PASSWORD" BLOCKS_PER_COMMIT=500 \
PROVIDER=redis-failure RUN_NUMBER=1 SCENARIO=verify \
RESULT_ROOT=/tmp/docsa-commit-cache-recovery/stopped \
k6 run perf/read/commit_content_benchmark.js

docker start docsa-redis-local
for attempt in $(seq 1 30); do
  docker exec docsa-redis-local redis-cli ping | grep -q PONG && break
  [[ "$attempt" == 30 ]] && { echo 'Redis health 확인 시간 초과' >&2; exit 1; }
  sleep 1
done

RUN_ID=commit-cache-b500 BASE_URL=http://localhost:8080 \
USER_PASSWORD="$USER_PASSWORD" BLOCKS_PER_COMMIT=500 \
PROVIDER=redis-recovered RUN_NUMBER=1 SCENARIO=verify \
RESULT_ROOT=/tmp/docsa-commit-cache-recovery/recovered \
k6 run perf/read/commit_content_benchmark.js
```

Redis key 정리는 runner가 설정된 `docsa:experiment:commit-content:` namespace만 scan한 뒤 `UNLINK`한다. 다른 기능의 key나 Redis DB 전체는 정리 대상이 아니다.

### Staging 제한

로컬 3회 비교로 선택된 provider 하나만 staging에서 500 Block, Mixed, VU 50, 60초 조건으로 3회 확인한다. staging에서는 포화 테스트를 실행하지 않으며 오류율이 상승하면 즉시 중단한다. 성능 테스트 계정과 `RUN_ID`를 운영 데이터와 분리하고, 로컬과 staging의 절대 응답시간을 합치지 않고 개선 방향이 같은지만 확인한다.
