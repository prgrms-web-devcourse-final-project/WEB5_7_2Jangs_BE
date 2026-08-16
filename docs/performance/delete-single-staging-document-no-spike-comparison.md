# 스테이징 단일 사용자 document 삭제 비교(변동 구간 제외)

- 비교 대상: baseline과 병합 후 변동 구간을 제외한 30회 × 3회 집계
- 지표: `op_doc_delete_ms`
- 검산 근거: [`stg-30x3-summary.json`](../../perf/delete/single/document/results/evidence/stg-30x3-summary.json)
- 변화율: `(병합 후 - 병합 전) / 병합 전 × 100`

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 3309.07 | 1112.07 | -66.39% | improved |
| delete p95 (ms) | 5555.59 | 1287.51 | -76.83% | improved |
| delete avg (ms) | 2853.63 | 1133.51 | -60.28% | improved |
| http_req_duration p95 (ms) | 2068.44 | 2053.18 | -0.74% | improved |
| http_req_duration avg (ms) | 1609.74 | 1560.33 | -3.07% | improved |
| http_req_failed rate | 0.0000 | 0.0000 | - | - |
| delete_failed rate | 0.0000 | 0.0000 | - | - |
