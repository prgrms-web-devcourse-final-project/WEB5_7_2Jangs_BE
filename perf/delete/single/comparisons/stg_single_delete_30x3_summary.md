# Staging Single-User Delete Benchmark (30 x 3)

- Date: 2026-03-10
- Base URL: https://192.168.0.143:8443
- Script: perf/delete/single/single_user_delete_benchmark.js
- Params: TARGET_COUNT=30, MAIN_COMMITS=8, FEATURE_COMMITS=5, BLOCKS_PER_COMMIT=500
- Rounds: doc 3회 + branch 3회 + commit 3회
- HTTP fail rate: all rounds 0

## Round Results

| target | round file | p50 (ms) | p95 (ms) | avg (ms) | max (ms) | run duration (s) |
|---|---|---:|---:|---:|---:|---:|
| branch | branch_delete_stg_branch_30x3_r1.json | 314.7 | 4334.4 | 1632.2 | 4382.8 | 832.5 |
| branch | branch_delete_stg_branch_30x3_r2.json | 236.7 | 4263.5 | 1514.8 | 4335.3 | 833.6 |
| branch | branch_delete_stg_branch_30x3_r3.json | 273.8 | 4313.4 | 1674.1 | 4392.8 | 838.3 |
| commit | commit_delete_stg_commit_30x3_r1.json | 86.9 | 3075.6 | 631.2 | 3082.3 | 807.1 |
| commit | commit_delete_stg_commit_30x3_r2.json | 81.2 | 2859.9 | 631.8 | 3093.8 | 805.2 |
| commit | commit_delete_stg_commit_30x3_r3.json | 85.0 | 2829.0 | 599.5 | 3099.0 | 805.1 |
| doc | doc_delete_stg_doc_30x3_r1.json | 4327.5 | 5652.4 | 3067.3 | 6273.3 | 876.2 |
| doc | doc_delete_stg_doc_30x3_r2.json | 1151.6 | 5478.9 | 2414.9 | 5693.2 | 859.5 |
| doc | doc_delete_stg_doc_30x3_r3.json | 4448.1 | 5535.6 | 3078.7 | 5609.1 | 882.5 |

## Aggregated (by target, 3 rounds)

| target | p50 mean | p50 median | p50 min~max | p95 mean | p95 median | p95 min~max | avg mean | avg median | avg min~max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| branch | 275.1 | 273.8 | 236.7 ~ 314.7 | 4303.8 | 4313.4 | 4263.5 ~ 4334.4 | 1607.0 | 1632.2 | 1514.8 ~ 1674.1 |
| commit | 84.4 | 85.0 | 81.2 ~ 86.9 | 2921.5 | 2859.9 | 2829.0 ~ 3075.6 | 620.8 | 631.2 | 599.5 ~ 631.8 |
| doc | 3309.1 | 4327.5 | 1151.6 ~ 4448.1 | 5555.6 | 5535.6 | 5478.9 ~ 5652.4 | 2853.6 | 3067.3 | 2414.9 ~ 3078.7 |

## Notes

- setup(시드) 시간이 길어 run duration이 길게 측정됨(약 805~883초).
- 삭제 지표(op_*_delete_ms)는 삭제 API 구간만 집계함.
- doc는 p50 변동 폭이 커서(라운드 간 편차) p95/avg 중심 해석이 안전함.
