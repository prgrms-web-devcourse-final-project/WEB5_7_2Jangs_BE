# Commit Create Benchmark

커밋 생성 Saga 적용 전후의 로컬 응답 시간을 비교한다. 공용
`perf/seed/commit_block_plan.mjs`를 사용해 매 요청마다 지정한 수만큼 새 블록을 생성한다.

## 측정 조건

- 로컬 애플리케이션과 로컬 MySQL·MongoDB만 사용한다.
- 로그인, 문서 생성, 그래프 조회는 `setup()`에서 수행해 측정값에서 제외한다.
- 단일 VU가 같은 문서에 커밋을 순차 생성한다.
- 측정 대상은 커밋 생성 HTTP 요청 시간이다.
- 허용하는 블록 수는 `50`, `200`이다.
- 기본적으로 3회 워밍업한 뒤 20회 측정하며, 워밍업 요청은 `commit_create_ms`에서 제외한다.
- `Idempotency-Key`는 매 생성 요청마다 새 UUID를 전달한다. Saga 적용 전 코드는 추가 헤더를 무시한다.

## 실행

```bash
mkdir -p perf/create/commit/results/staging/blocks-50/run-1
VERSION=staging BLOCKS_PER_COMMIT=50 ITERATIONS=20 WARMUP_ITERATIONS=3 RUN_NUMBER=1 \
RUN_ID=staging-b50 BASE_URL=http://localhost:8080 \
k6 run perf/create/commit/commit_create_benchmark.js

mkdir -p perf/create/commit/results/optimized/blocks-200/run-1
VERSION=optimized BLOCKS_PER_COMMIT=200 ITERATIONS=20 WARMUP_ITERATIONS=3 RUN_NUMBER=1 \
RUN_ID=optimized-b200 BASE_URL=http://localhost:8080 \
k6 run perf/create/commit/commit_create_benchmark.js
```

결과는 다음 위치에 저장된다.

```text
perf/create/commit/results/<version>/blocks-<count>/run-<number>/summary.json
```

`commit_create_ms`에는 측정 요청의 평균, 중앙값, p90, p95, 최솟값과 최댓값이 기록된다.
20회는 로컬 성능 회귀의 방향을 확인하기 위한 최소 표본이므로 중앙값과 p90을 우선 해석하고,
p95는 참고값으로만 사용한다.

## 데이터 정리

로컬 프로필의 `LocalMongoCleanup`과 Hibernate `create-drop` 설정으로 애플리케이션 시작·종료 시
측정 데이터가 정리된다. 애플리케이션이 비정상 종료된 경우 다음 실행 전에 로컬 데이터베이스를
초기화하거나 고유한 `RUN_ID`를 사용한다.
