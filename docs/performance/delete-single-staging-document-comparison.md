# 스테이징 단일 사용자 document 삭제 비교

- 비교 대상: outbox 병합 전후의 30회 × 3회 집계
- 지표: `op_doc_delete_ms`
- 검산 근거: [`stg-30x3-summary.json`](../../perf/delete/single/document/results/evidence/stg-30x3-summary.json)
- 변화율: `(병합 후 - 병합 전) / 병합 전 × 100`

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 3309.07 | 1114.63 | -66.32% | improved |
| delete p95 (ms) | 5555.59 | 5279.28 | -4.97% | improved |
| delete avg (ms) | 2853.63 | 1682.53 | -41.04% | improved |
| http_req_duration p95 (ms) | 2068.44 | 2058.12 | -0.50% | improved |
| http_req_duration avg (ms) | 1609.74 | 1603.16 | -0.41% | improved |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
