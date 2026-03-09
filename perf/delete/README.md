# Deletion-Only Performance Benchmark (Multi-user)

목표:

- 다수 유저가 실사용처럼 문서/커밋/브랜치를 먼저 생성
- 삭제 API 성능만 별도 측정
- `이벤트 기반 삭제` vs `outbox` 비교를 같은 조건으로 수행

## Files

- `perf/delete/seed_dataset.js`
  - 유저별 문서/커밋/브랜치 데이터 생성
- `perf/delete/delete_only_benchmark.js`
  - 생성된 문서를 찾아 삭제만 수행
- `perf/delete/compare_delete_summary.mjs`
  - 두 브랜치 결과 비교 표 출력

## 0) App run (example)

로컬 부팅 예시:

```bash
MONGO_URI='...' MYSQL_USERNAME='devbae' MYSQL_PASSWORD='' SPRING_PROFILES_ACTIVE='local' bash ./gradlew bootRun
```

`dev` 브랜치에서 메일 env가 필요하면 아래도 같이 추가:

```bash
MAIL_USERNAME='...' MAIL_PASSWORD='...'
```

## 1) Prepare users (no script)

유저 생성은 애플리케이션의 `TestUserInitializer`를 사용합니다.

```bash
PERF_SEED_USER_COUNT=100
```

유저 패턴:

- `perfdel_u001@test.com` ~ `perfdel_u100@test.com`
- 비밀번호: `Testtest1`

## 2) Seed realistic dataset

동일한 `RUN_ID`를 기록해 두고 삭제 벤치마크에서 재사용하세요.

```bash
RUN_ID=run01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfdel USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=100 DOCS_PER_USER=3 \
MAIN_COMMITS=6 FEATURE_COMMITS=4 BLOCKS_PER_COMMIT=20 \
SEED_VUS=20 \
k6 run perf/delete/seed_dataset.js
```

생성 규칙:

- 문서 제목: `PDEL-<RUN_ID>uNNNdNNN`
- 각 문서에 main 커밋 + feature 브랜치 커밋 생성
- 삭제 시 block 수가 충분히 커지도록 commit 당 block 수 지정

## 3) Delete-only benchmark

```bash
RUN_ID=run01 \
BASE_URL=http://localhost:8080 \
USER_PREFIX=perfdel USER_DOMAIN=test.com USER_PASSWORD=Testtest1 \
USER_COUNT=100 DOCS_PER_USER=3 \
DELETE_VUS=30 \
k6 run perf/delete/delete_only_benchmark.js \
  --summary-export perf/delete/results/delete_only_summary.json
```

측정 포인트:

- `op_doc_delete_ms` (핵심)
- `http_req_duration`
- 실패율: `http_req_failed`, `delete_failed`

## 4) Branch comparison workflow

권장: 브랜치별로 아래 순서를 동일하게 반복

1. 브랜치 checkout
2. 서버 실행
3. 유저 준비(`PERF_SEED_USER_COUNT` 적용된 상태)
4. 데이터 생성 (`seed_dataset.js`)
5. 삭제 벤치 실행 (`delete_only_benchmark.js`)
6. 결과 파일 저장

예시 결과 파일명:

- `perf/delete/results/dev_delete_summary.json`
- `perf/delete/results/refactor_delete_summary.json`

비교:

```bash
node perf/delete/compare_delete_summary.mjs \
  perf/delete/results/dev_delete_summary.json \
  perf/delete/results/refactor_delete_summary.json
```

## 5) Fairness checklist

- 브랜치별 동일한 `USER_COUNT`, `DOCS_PER_USER`, `MAIN_COMMITS`, `FEATURE_COMMITS`, `BLOCKS_PER_COMMIT`
- 동일 DB 스펙, 동일 JVM 옵션
- 워밍업 1회 후 본측정 3회 이상 (평균/편차 사용)
- 브랜치 순서 바꿔서 반복 (`dev -> refactor`, `refactor -> dev`)
