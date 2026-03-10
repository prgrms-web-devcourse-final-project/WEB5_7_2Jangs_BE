# Single-User Heavy Delete Benchmark

목표:
- 단일 유저 기준으로, 블록 수가 많은 데이터에서 삭제 성능을 측정
- 삭제 대상별 분리 측정: `doc`, `branch`, `commit`
- `이벤트 기반 삭제` vs `outbox` 비교를 같은 조건으로 수행

## What this benchmark does

1. `setup()`에서 단일 유저로 무거운 데이터셋을 생성
- 문서 N개 생성
- 각 문서에 main 커밋 + feature 브랜치 + feature 커밋 생성
- 커밋당 block 수를 크게 설정 가능

2. 본 실행에서는 삭제만 수행
- `TARGET=doc`    -> 문서 삭제 API만 측정
- `TARGET=branch` -> 브랜치 삭제 API만 측정
- `TARGET=commit` -> 커밋 삭제 API만 측정

## Files

- `single_user_delete_benchmark.js`
  - 단일 유저/대용량 블록 삭제 벤치
- `compare_single_delete_summary.mjs`
  - 두 결과 JSON 비교 표 출력
- `results/`
  - 실행 결과 JSON 저장

## Environment variables

- `BASE_URL` (default: `http://localhost:8080`)
- `TEST_EMAIL` (default: `test@test.com`)
- `TEST_PASSWORD` (default: `Testtest1`)
- `TARGET` (`doc` | `branch` | `commit`, default: `doc`)
- `TARGET_COUNT` (삭제할 대상 개수, default: `5`)
- `MAIN_COMMITS` (default: `6`)
- `FEATURE_COMMITS` (default: `4`)
- `BLOCKS_PER_COMMIT` (default: `300`)
- `RUN_ID` (default: timestamp)
- `DELETE_MAX_DURATION` (default: `40m`)
- `DELETE_P95_THRESHOLD_MS` (default: `15000`)
- `RESULT_DIR` (default: `perf/single_delete/results`)

## Run examples

### 1) Heavy document delete (single user)

```bash
BASE_URL='http://localhost:8080' \
TEST_EMAIL='test@test.com' TEST_PASSWORD='Testtest1' \
TARGET='doc' TARGET_COUNT=5 \
MAIN_COMMITS=8 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=500 \
RUN_ID='doc_run01' \
k6 run perf/single_delete/single_user_delete_benchmark.js
```

### 2) Heavy branch delete (single user)

```bash
BASE_URL='http://localhost:8080' \
TEST_EMAIL='test@test.com' TEST_PASSWORD='Testtest1' \
TARGET='branch' TARGET_COUNT=5 \
MAIN_COMMITS=8 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=500 \
RUN_ID='branch_run01' \
k6 run perf/single_delete/single_user_delete_benchmark.js
```

### 3) Heavy commit delete (single user)

```bash
BASE_URL='http://localhost:8080' \
TEST_EMAIL='test@test.com' TEST_PASSWORD='Testtest1' \
TARGET='commit' TARGET_COUNT=5 \
MAIN_COMMITS=8 FEATURE_COMMITS=5 BLOCKS_PER_COMMIT=500 \
RUN_ID='commit_run01' \
k6 run perf/single_delete/single_user_delete_benchmark.js
```

## Branch comparison

동일한 `TARGET`/파라미터로 `dev`, `Refactor/178-outbox`를 각각 실행한 뒤:

```bash
node perf/single_delete/compare_single_delete_summary.mjs \
  perf/single_delete/results/<base>.json \
  perf/single_delete/results/<target>.json
```

## Notes

- 이 벤치는 `setup()`에서 데이터를 생성하고, 본 실행에서 삭제만 측정합니다.
- `commit` 삭제는 서비스 제약(leaf이며 root/from commit이 아니어야 함)을 만족하도록 데이터셋을 생성합니다.
- 진짜 single-user 조건 유지를 위해 실행 VU를 강제로 1로 고정했습니다.
