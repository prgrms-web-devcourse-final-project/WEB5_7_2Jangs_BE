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

캐시 없이 앱을 기동한 뒤, block 수마다 다른 `RUN_ID`로 20명 × 문서 2개 × main commit 10개의 데이터를 만든다. 같은 `RUN_ID`를 반복하면 데이터가 겹칠 수 있으므로, 재실행 전에는 기존 성능 데이터를 정리한다.

```bash
COMMIT_CONTENT_CACHE_ENABLED=false \
docker compose -f infra/docker-compose.local.yml up -d --build --force-recreate app

RUN_ID='commit-cache-b500' \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfuser USER_DOMAIN=test.com USER_PASSWORD="$USER_PASSWORD" \
USER_COUNT=20 DOCS_PER_USER=2 MAIN_COMMITS=10 FEATURE_BRANCHES=0 FEATURE_COMMITS=0 \
BLOCKS_PER_COMMIT=500 COMMIT_BLOCK_CHANGE_RATE=0.1 SEED_VUS=5 \
k6 run perf/seed/seed_dataset.js
```

## 실행

한 조건은 다음처럼 실행한다. runner는 앱 재생성, health 확인, warm-up, 측정 구간 전후 관측값 수집을 수행한다.

```bash
PROVIDER=none BLOCKS_PER_COMMIT=500 PATTERN=hot RUN_NO=1 \
RUN_ID='commit-cache-b500' BASE_URL=http://localhost:8080 \
USER_PASSWORD="$USER_PASSWORD" HOT_VUS=50 HOT_DURATION=60s \
bash perf/read/commit-cache/run_commit_cache_matrix.sh
```

결과는 `results/<provider>/blocks-<count>/<pattern>/<load-profile>/run-<1|2|3>/`에 저장된다. `summary.json`과 전후 Prometheus·container snapshot에는 비밀번호, Mongo URI, 세션 cookie를 저장하지 않는다.

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
