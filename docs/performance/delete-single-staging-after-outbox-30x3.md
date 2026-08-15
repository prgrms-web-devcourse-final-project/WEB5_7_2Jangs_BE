# Staging Single-User Delete Benchmark (After Outbox Merge, 30x3)

- Date: 2026-03-11
- Base URL: https://192.168.0.143:8443
- Params: TARGET_COUNT=30, MAIN_COMMITS=8, FEATURE_COMMITS=5, BLOCKS_PER_COMMIT=500
- Rounds: doc 3회 + branch 3회 + commit 3회
- 검산 근거: 대상별 [`document`](../../perf/delete/single/document/results/evidence/stg-30x3-summary.json) · [`branch`](../../perf/delete/single/branch/results/evidence/stg-30x3-summary.json) · [`commit`](../../perf/delete/single/commit/results/evidence/stg-30x3-summary.json) 집계값

원본 raw 결과는 인증·환경 정보와 용량 문제로 커밋하지 않는다. 아래 비교 수치는 위 비민감 집계값으로 재계산할 수 있다.

## Round Results (After Merge)

| target | round | p50 (ms) | p95 (ms) | avg (ms) | max (ms) | run duration (s) | fail rate |
|---|---:|---:|---:|---:|---:|---:|---:|
| doc | 1 | 1127.3 | 8479.1 | 2176.7 | 10125.7 | 848.6 | 0.000 |
| doc | 2 | 1105.1 | 6003.0 | 1733.2 | 10038.8 | 883.4 | 0.000 |
| doc | 3 | 1111.5 | 1355.7 | 1137.7 | 1488.3 | 874.1 | 0.000 |
| branch | 1 | 456.2 | 568.9 | 465.4 | 661.2 | 892.4 | 0.000 |
| branch | 2 | 476.1 | 560.7 | 474.1 | 578.2 | 853.7 | 0.000 |
| branch | 3 | 487.7 | 3253.9 | 878.7 | 7268.5 | 862.9 | 0.000 |
| commit | 1 | 106.7 | 189.8 | 120.4 | 299.2 | 813.6 | 0.000 |
| commit | 2 | 101.0 | 116.8 | 106.7 | 295.8 | 800.6 | 0.000 |
| commit | 3 | 108.4 | 183.7 | 121.5 | 255.1 | 802.4 | 0.000 |

## Aggregated Comparison (Before vs After, each 3-run average)

| target | before p95 (ms) | after p95 (ms) | p95 개선율 | before avg (ms) | after avg (ms) | avg 개선율 |
|---|---:|---:|---:|---:|---:|---:|
| doc | 5555.6 | 5279.3 | 5.0% | 2853.6 | 1682.5 | 41.0% |
| branch | 4303.8 | 1461.2 | 66.0% | 1607.0 | 606.1 | 62.3% |
| commit | 2921.5 | 163.4 | 94.4% | 620.8 | 116.2 | 81.3% |
