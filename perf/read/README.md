# Document List Performance Benchmark

목표:

- 문서 목록 조회 경로의 읽기 성능을 별도로 측정
- `sidebar`(최근 활동만)와 `full list`(preview 포함), `search`(preview 포함)를 분리 비교
- 문서 수, branch 수, preview 조립 비용이 응답 시간에 미치는 영향을 확인

## Files

- `perf/delete/seed_dataset.js`
  - 목록 테스트에 사용할 문서/브랜치/커밋 데이터를 생성
- `perf/read/doc_list_benchmark.js`
  - `sidebar`, `full list`, `search` 읽기 성능 측정

## Why this benchmark

현재 목록 조회는 `Doc` 페이지 조회 후, 각 문서마다 최근 branch를 찾고 preview를 조립합니다.
즉 `sidebar`는 JPA 연관 로딩 비용, `full list`와 `search`는 여기에 Mongo preview 조립 비용까지 추가로 확인할 수 있습니다.

## 1) Prepare users

유저 생성은 애플리케이션의 `TestUserInitializer`를 사용합니다.

```bash
PERF_SEED_USER_COUNT=50
```

유저 패턴:

- `perfdel_u001@test.com` ~ `perfdel_u050@test.com`
- 비밀번호: `Testtest1`

## 2) Seed dataset

삭제 벤치에서 쓰던 seed 스크립트를 그대로 재사용합니다.
중요한 건 목록 조회 시 문서별 branch/commit이 충분히 많아지도록 데이터를 만드는 것입니다.

```bash
RUN_ID=read01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfdel USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=50 DOCS_PER_USER=5 \
MAIN_COMMITS=8 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=100 \
SEED_VUS=20 \
k6 run perf/delete/seed_dataset.js
```

권장 기준:

- `DOCS_PER_USER`: 5 이상
- `MAIN_COMMITS`: 8 이상
- `FEATURE_COMMITS`: 5 이상
- `BLOCKS_PER_COMMIT`: 100 이상

## 3) Run list benchmark

```bash
RUN_ID=read01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfdel USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=50 DOCS_PER_USER=5 PAGE_SIZE=10 \
SIDEBAR_VUS=10 FULL_LIST_VUS=10 SEARCH_VUS=10 \
k6 run perf/read/doc_list_benchmark.js
```

결과 파일:

- `perf/read/results/doc_list_benchmark_<RUN_ID>.json`

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
