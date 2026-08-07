# Single-User Heavy Delete Comparison
- base:   ../document/results/doc_delete_stg_30x3_avg.json
- target: ../document/results/doc_delete_stg_aftermerge_30x3_avg.json
- metric: op_doc_delete_ms

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 3309.07 | 1114.63 | -66.32% | improved |
| delete p95 (ms) | 5555.59 | 5279.28 | -4.97% | improved |
| delete avg (ms) | 2853.63 | 1682.53 | -41.04% | improved |
| http_req_duration p95 (ms) | 2068.44 | 2058.12 | -0.50% | improved |
| http_req_duration avg (ms) | 1609.74 | 1603.16 | -0.41% | improved |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
