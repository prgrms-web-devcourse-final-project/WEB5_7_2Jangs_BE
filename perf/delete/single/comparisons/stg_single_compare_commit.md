# Single-User Heavy Delete Comparison
- base:   ../commit/results/commit_delete_stg_30x3_avg.json
- target: ../commit/results/commit_delete_stg_aftermerge_30x3_avg.json
- metric: op_commit_delete_ms

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 84.37 | 105.36 | 24.88% | regressed |
| delete p95 (ms) | 2921.49 | 163.42 | -94.41% | improved |
| delete avg (ms) | 620.82 | 116.23 | -81.28% | improved |
| http_req_duration p95 (ms) | 2056.85 | 2052.83 | -0.20% | improved |
| http_req_duration avg (ms) | 1485.74 | 1486.03 | 0.02% | regressed |
| http_req_failed rate | - | - | - | - |
| delete_failed rate | - | - | - | - |
