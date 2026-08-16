# 스테이징 단일 사용자 branch 삭제 비교

- 비교 대상: outbox 병합 전후의 30회 × 3회 집계
- 지표: `op_branch_delete_ms`
- 검산 근거: [`stg-30x3-summary.json`](../../perf/delete/single/branch/results/evidence/stg-30x3-summary.json)
- 변화율: `(병합 후 - 병합 전) / 병합 전 × 100`

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 275.08 | 473.34 | 72.07% | regressed |
| delete p95 (ms) | 4303.76 | 1461.18 | -66.05% | improved |
| delete avg (ms) | 1607.01 | 606.07 | -62.29% | improved |
| http_req_duration p95 (ms) | 2072.65 | 2087.51 | 0.72% | regressed |
| http_req_duration avg (ms) | 1539.53 | 1604.43 | 4.22% | regressed |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
