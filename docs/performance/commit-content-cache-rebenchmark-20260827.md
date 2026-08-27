# 커밋 본문 Caffeine 캐시 재측정

## 재측정 이유

- 기존 Mixed 측정은 400개 전체 target을 사전 실행해 maximum size 400인 캐시를 모두 채웠다.
- 본 측정의 cache miss와 본문 assemble delta가 0이어서, 80% Hot / 20% Cold가 아니라 넓은 Hot workload에 가까웠다.
- 기존 `Mixed p95 19.4% 개선`은 해당 조건을 근거로 사용할 수 없어 최종 `@Cacheable(sync = true)` 구현을 다시 측정했다.

## 조건

- 환경: 로컬 단일 인스턴스, No Cache와 Caffeine 비교
- 데이터: 20 users × 5 docs × 20 main commits = 2,000 commit targets
- 본문: commit당 500 blocks, commit마다 10% block 변경
- 캐시: maximum size 400, TTL 10분
- 부하: VU50, 60초, 조건별 5회
- 실행 순서: 1·3·5회 `none → caffeine`, 2·4회 `caffeine → none`
- Hot: user당 4개, 총 80개 key를 사전 적재하고 반복 조회
- Mixed: 같은 80개 Hot key에 80%, 나머지 1,920개 key에 20% 요청
- 판정 기준: paired 변화 방향, No Cache 반복 변동폭, 최소 의미 차이 `max(10%, baseline 변동폭)`

## 결과

| 조건 | Provider | p50 평균 | p95 평균 | p99 평균 | 처리량 평균 |
|---|---|---:|---:|---:|---:|
| Hot | No Cache | 32.49 ms | 110.79 ms | 316.13 ms | 321.52 req/s |
| Hot | Caffeine | 25.18 ms | 96.25 ms | 268.62 ms | 362.72 req/s |
| Mixed | No Cache | 35.06 ms | 100.07 ms | 275.63 ms | 312.85 req/s |
| Mixed | Caffeine | 29.54 ms | 98.55 ms | 266.94 ms | 333.89 req/s |

| 조건 | p50 paired 개선 | p95 paired 개선 | 처리량 paired 개선 | 해석 |
|---|---:|---:|---:|---|
| Hot | 평균 22.5%, 5/5 개선 | 평균 12.8%, 4/5 개선 | 평균 12.9%, 5/5 개선 | p50과 처리량은 핵심 성과로 채택하고 p95는 보조 지표로 사용 |
| Mixed | 평균 15.7%, 5/5 개선 | 참고값 | 평균 6.7%, 5/5 개선 | 20% cache miss 조건에서 p95가 miss 경로를 포함하므로 직접 효과 평가에서 제외 |

Hot p95는 5회 중 4회에서 감소했고 paired 평균은 12.8%였다. 모든 요청이 캐시에 적중한 Hot 조건의 tail latency이므로 보조 성과로 사용한다. Mixed는 요청의 20%가 cache miss가 되도록 구성했다. 이 조건의 p95는 miss 경로에 해당하므로 캐시 적중에 따른 직접 성능 개선 지표로 사용하지 않는다.

처리량은 setup과 measurement gate 대기를 포함하는 `iterations.values.rate`를 사용하지 않고, 측정 구간 완료 요청 수인 `iterations.values.count / 60초`로 계산했다.

## 캐시 동작 검산

| 조건 | Caffeine hit ratio | miss / assemble | eviction | No Cache assemble | assemble 감소 |
|---|---:|---:|---:|---:|---:|
| Hot | 100% (5/5) | 0 / 0 | 0 | 평균 19,291회 | 100% |
| Mixed | 81.07~81.47% | 평균 3,765회 / 3,765회 | 평균 3,445회 | 평균 18,771회 | 79.9% |

- Hot은 측정 구간 5회 모두 cache miss와 본문 assemble이 0이었다.
- Mixed는 매회 miss와 eviction이 발생해 working set 전체가 미리 캐시되지 않았음을 확인했다.
- No Cache는 요청 수와 assemble 횟수가 매회 일치했고, Caffeine Mixed는 miss와 assemble 횟수가 매회 일치했다.
- 오류율과 dropped iteration은 모든 실행에서 0이었다.

## 결론과 포트폴리오 반영

- 핵심 성과: `Hot p50 22.5% 개선, 처리량 12.9% 증가`, `Mixed p50 15.7% 개선`, `Mixed 본문 조립 79.9% 감소`.
- 보조 성과: Hot p95는 평균 12.8% 감소했고 5회 중 4회에서 개선됐다.
- Mixed p95는 20% cache miss를 포함하는 tail latency이므로 캐시의 직접 효과를 평가하는 지표에서 제외한다.
- 철회할 성과: 기존 `Hot p95 17.8%`, `Mixed p95 19.4%`.
- 이번 결과는 캐시 자체의 효과와 Caffeine 구현의 유효성을 확인한다. Redis를 같은 수정 조건으로 재측정하지 않았으므로 Caffeine이 Redis보다 빠르다는 근거로 사용하지 않는다.
- 단일 인스턴스에서 분산 캐시의 네트워크 경로와 운영 복잡도를 추가할 필요가 없다는 선택 근거와, 이번 No Cache 대비 개선 결과를 분리해 설명한다.

## 제한

- 로컬 단일 인스턴스 결과이며 운영 트래픽 분포를 그대로 재현하지 않는다.
- 500 blocks, VU50 조건만 다루므로 다른 본문 크기와 부하에서는 별도 검증이 필요하다.
- Hot p95는 반복 방향과 로컬 환경 변동을 고려해 보조 지표로만 사용한다. Mixed p95는 20% miss 조건의 tail latency로 해석한다.
