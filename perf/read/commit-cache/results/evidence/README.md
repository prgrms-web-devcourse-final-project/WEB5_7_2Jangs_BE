# 커밋 내용 캐시 검증 증거

이 디렉터리는 전체 부하 테스트 raw 결과를 대신하는 최소 검증 산출물이다.

- `caffeine-comparison-summary.json`: Hot/Mixed 3회 p95 원자료, 평균 계산, 캐시 관측
- `caffeine-comparison-prometheus-after.txt`: 위 비교의 핵심 Prometheus after 값
- `single-flight-summary.json`: 최종 어노테이션 동시 요청 50건의 요약
- `single-flight-prometheus-before.txt`, `single-flight-prometheus-after.txt`: Single-Flight 전후 핵심 메트릭
- `core-comparison-summary.json`: 과거 3-provider 핵심 6조건(Hot/Mixed × VU 20·50·100)의 비민감 3회 평균과 자원 관측값
- `core-comparison-run-summary.json`: 위 6조건의 run별 p95·처리량·오류·dropped 요약
- `rebenchmark-20260827-summary.json`: 최종 `@Cacheable(sync = true)` 구현의 Hot/Mixed 5회 재측정 원자료와 계산식

## 재현성 범위

Hot/Mixed 비교 결과의 실제 측정 revision은 `62a2d1949ad81a8db75cc1609222b04477cb5c3f`이다. 이 revision은 수동 Caffeine 캐시 구현을 사용한 비교 실험이며, 최종 `@Cacheable(sync = true)` 구현의 Hot/Mixed 성능을 직접 측정한 결과가 아니다.

최종 어노테이션 구현은 `8fab327`이며, 해당 구현의 동작 검증 결과는 `single-flight-summary.json`에 별도로 기록한다. 전체 raw 결과는 인증·환경 정보와 용량 문제로 커밋하지 않는다.

`core-comparison-summary.json`은 과거 수동 캐시 실험의 집계 근거다. `meanFields`의 순서대로 각 provider 배열을 해석하면 보고서의 p95·처리량·heap/GC·Redis memory 수치를 재계산할 수 있다. `core-comparison-run-summary.json`은 같은 run 번호의 p95 변화율과 baseline 변동폭을 검산한다. 이 결과를 최종 `@Cacheable(sync = true)` 구현의 성능으로 일반화하지 않는다.

`rebenchmark-20260827-summary.json`은 2,000 targets, cache maximum size 400, 500 blocks, VU50·60초 조건의 none/Caffeine 각 5회 결과를 담는다. `runFields` 순서로 각 배열을 해석하며 처리량은 setup과 gate 대기를 제외하기 위해 `iterations / 60초`로 계산한다. Hot은 80개 key만 사전 적재하고 Mixed는 80% Hot / 20% 나머지 1,920개 key 분포를 사용한다.
