# Read Performance Benchmarks

| 시나리오 | 위치 | 목적 |
| --- | --- | --- |
| Commit cache | `commit-cache/` | 커밋 본문 조립 캐시 비교와 동시 요청 검증 |
| CQRS | `cqrs/` | 문서 목록과 read model 조회 비교 |
| Graph | `graph/` | 문서 graph 조회 측정 |
| Simple probes | `simple-read-probes/results/` | 과거 탐색적 조회 측정 결과 |

공용 데이터셋 생성은 `perf/seed/seed_dataset.js`를 사용한다. 각 시나리오의 raw 결과는 해당 시나리오의 `results/`에 저장한다.
