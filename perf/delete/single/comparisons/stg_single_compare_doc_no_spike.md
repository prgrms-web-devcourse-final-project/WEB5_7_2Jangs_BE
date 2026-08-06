# Single-User Heavy Delete Comparison
- base:   ../document/results/doc_delete_stg_30x3_avg.json
- target: ../document/results/doc_delete_stg_aftermerge_30x3_no_spike_avg.json
- metric: op_doc_delete_ms

| Metric | base | target | Delta | Direction |
|---|---:|---:|---:|---|
| delete p50 (ms) | 3309.07 | 1112.07 | -66.39% | improved |
| delete p95 (ms) | 5555.59 | 1287.51 | -76.83% | improved |
| delete avg (ms) | 2853.63 | 1133.51 | -60.28% | improved |
| http_req_duration p95 (ms) | 2068.44 | 2053.18 | -0.74% | improved |
| http_req_duration avg (ms) | 1609.74 | 1560.33 | -3.07% | improved |
| http_req_failed rate | 0.0000 | 0.0000 | - | - |
| delete_failed rate | 0.0000 | 0.0000 | - | - |
