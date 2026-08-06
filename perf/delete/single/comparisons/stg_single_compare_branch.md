# Single-User Heavy Delete Comparison
- base:   ../branch/results/branch_delete_stg_30x3_avg.json
- target: ../branch/results/branch_delete_stg_aftermerge_30x3_avg.json
- metric: op_branch_delete_ms

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 275.08 | 473.34 | 72.07% | regressed |
| delete p95 (ms) | 4303.76 | 1461.18 | -66.05% | improved |
| delete avg (ms) | 1607.01 | 606.07 | -62.29% | improved |
| http_req_duration p95 (ms) | 2072.65 | 2087.51 | 0.72% | regressed |
| http_req_duration avg (ms) | 1539.53 | 1604.43 | 4.22% | regressed |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
