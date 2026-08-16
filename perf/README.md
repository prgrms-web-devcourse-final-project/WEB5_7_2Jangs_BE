# Performance Benchmarks

성능 측정은 `도메인 → 시나리오 → 결과` 순서로 정리한다.

```text
perf/
├── seed/                 # 공용 데이터셋 생성기
├── read/                 # 조회 성능
├── delete/               # bulk/single 삭제 성능
└── thumbnail/            # preview E2E/workflow 성능
```

각 시나리오는 실행 파일과 `results/`를 함께 둔다. raw 결과는 Git에서 제외하고, 검산에 필요한 작은 산출물만 `results/evidence/`에 저장한다. 사람이 읽는 최종 판정 문서는 `docs/performance/`에 둔다.
