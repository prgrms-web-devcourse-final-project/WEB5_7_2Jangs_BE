# 커밋 내용 캐시 검증 증거

이 디렉터리는 전체 부하 테스트 raw 결과를 대신하는 최소 검증 산출물이다.

- `caffeine-comparison-summary.json`: Hot/Mixed 3회 p95 원자료, 평균 계산, 캐시 관측
- `caffeine-comparison-prometheus-after.txt`: 위 비교의 핵심 Prometheus after 값
- `single-flight-summary.json`: 최종 어노테이션 동시 요청 50건의 요약
- `single-flight-prometheus-before.txt`, `single-flight-prometheus-after.txt`: Single-Flight 전후 핵심 메트릭

## 재현성 범위

Hot/Mixed 비교 결과의 실제 측정 revision은 `62a2d1949ad81a8db75cc1609222b04477cb5c3f`이다. 이 revision은 수동 Caffeine 캐시 구현을 사용한 비교 실험이며, 최종 `@Cacheable(sync = true)` 구현의 Hot/Mixed 성능을 직접 측정한 결과가 아니다.

최종 어노테이션 구현은 `8fab327`이며, 해당 구현의 동작 검증 결과는 `single-flight-summary.json`에 별도로 기록한다. 전체 raw 결과는 인증·환경 정보와 용량 문제로 커밋하지 않는다.
