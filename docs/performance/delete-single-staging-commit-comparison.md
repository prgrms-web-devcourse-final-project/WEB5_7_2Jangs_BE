# 스테이징 단일 사용자 commit 삭제 비교

- 비교 대상: outbox 병합 전후의 30회 × 3회 집계
- 지표: `op_commit_delete_ms`
- 검산 근거: [`stg-30x3-summary.json`](../../perf/delete/single/commit/results/evidence/stg-30x3-summary.json)
- 변화율: `(병합 후 - 병합 전) / 병합 전 × 100`

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 84.37 | 105.36 | 24.88% | regressed |
| delete p95 (ms) | 2921.49 | 163.42 | -94.41% | improved |
| delete avg (ms) | 620.82 | 116.23 | -81.28% | improved |
| http_req_duration p95 (ms) | 2056.85 | 2052.83 | -0.20% | improved |
| http_req_duration avg (ms) | 1485.74 | 1486.03 | 0.02% | regressed |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
