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
