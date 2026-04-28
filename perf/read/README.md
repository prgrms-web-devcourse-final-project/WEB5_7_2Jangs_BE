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
MAIN_COMMITS=8 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=100 \
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

## 2026-04-28 dev vs thumbnail branch result

비교 목적:

- 텍스트 preview 조립 방식(`dev`)과 썸네일 기반 목록 조회 방식(`perf/thumbnail-test`)의 사용자 체감 조회 성능 비교
- 동일한 API seed 스크립트로 `10 users * 20 docs = 200 docs`를 생성
- 각 문서는 main branch commit 4개, feature branch commit 2개, commit당 block 20개를 포함
- 각 브랜치에서 같은 조건으로 3회 측정

측정 조건:

```bash
USER_COUNT=10
DOCS_PER_USER=20
PAGE_SIZE=20
SIDEBAR_VUS=10
FULL_LIST_VUS=10
SEARCH_VUS=10
SIDEBAR_DURATION=30s
FULL_LIST_DURATION=30s
SEARCH_DURATION=30s
DATASET_RUN_ID=u10d20
```

원본 결과:

- `dev`: `perf/read/results/thumbnail-vs-preview-u10d20/preview-dev/r1.json` ~ `r3.json`
- `thumbnail`: `perf/read/results/thumbnail-vs-preview-u10d20/thumbnail-current/r1.json` ~ `r3.json`

주의:

- `search`는 `DATASET_RUN_ID=u10d20`로 seed 문서 제목과 검색어 조건을 일치시켜 재측정했습니다.
- `sidebar`는 preview/thumbnail 조립 경로가 아니므로 성능 개선 주장 근거로 사용하지 않습니다.

요청별 평균:

| Branch | Request | Scenario | Avg | p95 | Note |
| --- | --- | --- | ---: | ---: | --- |
| `dev` | `POST /api/user/login` | all scenarios | 80.496 ms | 108.792 ms | 세션 확보 |
| `dev` | `GET /api/document/sidebar` | sidebar list | 5.825 ms | 7.804 ms | preview 조립 없음 |
| `dev` | `GET /api/document` | full list | 1847.203 ms | 2000.666 ms | 본문 preview 조립 포함 |
| `dev` | `GET /api/document/search` | search list | 1880.132 ms | 2006.982 ms | 검색 + 본문 preview 조립 포함 |
| `thumbnail` | `POST /api/user/login` | all scenarios | 79.254 ms | 93.538 ms | 세션 확보 |
| `thumbnail` | `GET /api/document/sidebar` | sidebar list | 9.980 ms | 13.276 ms | preview 조립 없음 |
| `thumbnail` | `GET /api/document` | full list | 11.233 ms | 15.187 ms | thumbnail URL 참조 |
| `thumbnail` | `GET /api/document/search` | search list | 11.217 ms | 15.538 ms | 검색어 조건 일치 |

실행별 p95:

| Branch | Run | Login p95 | Sidebar p95 | Full list p95 | Search p95 | Failed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `dev` | 1 | 92.863 ms | 8.605 ms | 1999.447 ms | 2006.043 ms | 0% |
| `dev` | 2 | 115.169 ms | 7.289 ms | 2000.108 ms | 2007.753 ms | 0% |
| `dev` | 3 | 118.343 ms | 7.519 ms | 2002.444 ms | 2007.149 ms | 0% |
| `thumbnail` | 1 | 100.003 ms | 13.193 ms | 16.309 ms | 16.025 ms | 0% |
| `thumbnail` | 2 | 96.246 ms | 13.444 ms | 16.002 ms | 15.697 ms | 0% |
| `thumbnail` | 3 | 84.363 ms | 13.191 ms | 13.250 ms | 14.892 ms | 0% |

요약:

| Metric | `dev` avg p95 | `thumbnail` avg p95 | Change |
| --- | ---: | ---: | ---: |
| `GET /api/document` full list | 2000.666 ms | 15.187 ms | 99.24% latency reduction, 131.73x faster |
| `GET /api/document/search` search list | 2006.982 ms | 15.538 ms | 99.23% latency reduction, 129.16x faster |

결론:

- 이 수치는 기능 변경에 따른 백엔드 조회 경로 최적화로 표현하는 것이 적절합니다.
- 포트폴리오에는 "문서 목록에서 매 요청마다 본문 preview를 조립하던 구조를 썸네일 참조 방식으로 변경해, 동일 데이터셋 기준 목록 조회 p95를 약 2.0s에서 15.2ms로 단축"처럼 쓰는 것이 가장 정확합니다.
- "단순 성능 튜닝"보다는 "조회 응답 모델 변경을 통한 목록 조회 병목 제거"로 설명하는 편이 신빙성이 높습니다.
