# Commit Content Cache Benchmark

커밋 본문 조립의 Caffeine 캐시 비교와 동일 키 동시 요청 검증을 수행한다. 현재 운영 후보 검증은 캐시 비활성화(`none`)와 Caffeine만 비교한다. 과거 Redis 3-provider 실험은 [최종 결과 보고서](../../../docs/performance/commit-content-cache-result.md)에 근거와 한계를 보존한다.

## 사전 조건

- Docker daemon과 k6가 실행 가능해야 한다.
- 로컬 DB에 `perfuser_u001@test.com`부터 `perfuser_u020@test.com`까지의 성능 테스트 계정이 있어야 한다.
- 비밀번호는 shell의 `USER_PASSWORD`에만 설정하고, 명령어·결과 파일에 직접 기록하지 않는다.
- 로컬 health와 Prometheus endpoint는 각각 `http://localhost:9091/actuator/health`, `http://localhost:9091/actuator/prometheus`이다.

```bash
: "${USER_PASSWORD:?USER_PASSWORD를 먼저 설정하세요}"
docker compose -f infra/docker-compose.local.yml config
```

## 데이터셋 준비

캐시 없이 앱을 기동한 뒤, block 수마다 다른 `RUN_ID`로 20명 × 문서 5개 × main commit 20개, 총 2,000 commit의 데이터를 만든다. 캐시 maximum size 400보다 working set을 크게 유지한다. 같은 `RUN_ID`를 반복하면 데이터가 겹칠 수 있으므로, 재실행 전에는 기존 성능 데이터를 정리한다.

```bash
COMMIT_CONTENT_CACHE_ENABLED=false \
docker compose -f infra/docker-compose.local.yml up -d --build --force-recreate app

mkdir -p perf/read/commit-cache/results/rebenchmark/seed

RUN_ID='commit-cache-rebenchmark-b500' \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD="$USER_PASSWORD" \
USER_COUNT=20 DOCS_PER_USER=5 MAIN_COMMITS=20 FEATURE_BRANCHES=0 FEATURE_COMMITS=0 \
BLOCKS_PER_COMMIT=500 COMMIT_BLOCK_CHANGE_RATE=0.1 SEED_VUS=5 \
RESULT_DIR=perf/read/commit-cache/results/rebenchmark/seed \
k6 run perf/seed/seed_dataset.js
```

## 실행

한 조건은 다음처럼 실행한다. runner는 앱 재생성, health 확인, hot key 80개만 warm-up, 측정 구간 전후 관측값 수집을 수행한다. Mixed는 요청의 80%를 hot key로, 20%를 나머지 1,920개 key로 보내 캐시를 채운 채 측정하는 오류를 방지한다.

```bash
CANONICAL_REBENCHMARK=true \
PROVIDER=none BLOCKS_PER_COMMIT=500 PATTERN=hot RUN_NO=1 \
RUN_ID='commit-cache-rebenchmark-b500' BASE_URL=http://localhost:8080 \
USER_PASSWORD="$USER_PASSWORD" HOT_VUS=50 HOT_DURATION=60s \
bash perf/read/commit-cache/run_commit_cache_matrix.sh
```

Hot과 Mixed는 VU50·60초로 각각 5회 측정한다. 실행 순서는 1·3·5회 `none → caffeine`, 2·4회 `caffeine → none`으로 교대해 시간 경과와 실행 순서 편향을 줄인다. 결과는 `results/<provider>/blocks-<count>/<pattern>/<load-profile>/run-<1..5>/`에 저장된다. `summary.json`과 전후 Prometheus·container snapshot에는 비밀번호, Mongo URI, 세션 cookie를 저장하지 않는다.

첫 실행에서 현재 소스로 이미지를 빌드한 뒤 같은 이미지로 반복할 때는 `REBUILD_APP=false`를 사용한다. provider마다 앱 컨테이너는 계속 재생성되지만 Docker registry 조회와 재빌드는 생략된다.

판정에는 p50·p95·p99, 처리량, 오류와 dropped iteration뿐 아니라 Prometheus 측정 구간 delta의 cache hit/miss, eviction, 본문 assemble 횟수를 함께 사용한다. Mixed에서 miss와 eviction이 0이면 결과를 폐기한다.

과거 3-provider 결과를 해석할 때만 아래 비교 도구를 쓴다.

```bash
node perf/read/commit-cache/compare_commit_cache_results.mjs \
  --result-root perf/read/commit-cache/results \
  --blocks 500 --pattern hot --load-profile vus-20 \
  --redis-fail-open-passed
```

## 정리

이 시나리오가 만든 문서 데이터는 제목 prefix가 `perfuser`인 계정에 연결된다. 다음 명령으로 성능 문서를 정리한다. 실제 운영 데이터와 같은 DB에서는 실행하지 않는다.

```bash
BASE_URL='http://localhost:8080' \
MODE=multi USER_PREFIX=perfuser USER_DOMAIN=test.com USER_COUNT=20 \
USER_PASSWORD="$USER_PASSWORD" \
bash perf/cleanup_perf_docs.sh
```

## 참고

- 실행: `run_commit_cache_matrix.sh`
- 비교: `compare_commit_cache_results.mjs`
- 결과: `results/`
- 최종 판정: `docs/performance/commit-content-cache-result.html`
