# Read Model Projection 순서 안정화 설계

## 문제

`domain_event_outbox`는 앞으로 여러 consumer가 구독할 수 있는 범용 Domain Event Outbox로 확장할 수 있는 기반이다. 다만 현재 구현에서 실제 consumer는 `DocListProjector` 하나이며, 첫 번째 사용처는 문서 목록 Read Model 갱신이다.

따라서 이번 문제는 outbox 자체를 Projection 전용으로 축소하자는 의미가 아니다. 범용 outbox 기반은 유지하되, 첫 consumer인 `DocListProjector`가 이벤트 중복과 역순을 어떻게 처리할지 정책이 부족하다는 점을 보완하는 작업이다.

- `DomainEventOutboxWakeUpListener`는 특정 `outboxId`를 relay에 전달한다.
- `DomainEventOutboxRelay.run(Long outboxId)`는 전달받은 row 하나를 바로 처리한다.
- `DocListReadModel`은 `lastProjectedEventId` 하나로 모든 이벤트를 중복 처리한다.

이 조합에서는 같은 문서의 이벤트가 생성 순서와 다르게 처리될 수 있다. 예를 들어 썸네일 이벤트가 먼저 반영되면 `lastProjectedEventId`가 커지고, 그보다 오래된 activity 이벤트가 나중에 도착했을 때 `recentSaveId` 갱신이 버려질 수 있다.

반대로 오래된 이벤트를 무조건 허용하면 더 큰 문제가 생긴다. 오래된 title/activity 이벤트가 삭제된 문서를 다시 `deleted=false`로 되살리거나, `updatedAt`을 과거로 되돌릴 수 있다.

## 목표

이번 변경의 목표는 이벤트 시스템 전체를 새로 설계하는 것이 아니다. 현재 프로젝트 규모에 맞게 Doc List Read Model projection을 안전하게 만든다.

- Wakeup은 정확성 보장이 아니라 처리 지연 감소용 신호로 제한한다.
- Relay는 특정 outbox row를 건너뛰어 처리하지 않고 OPEN 이벤트를 생성 순서대로 처리한다.
- Projector는 중복 이벤트와 역순 이벤트를 견딘다.
- 삭제 이벤트는 terminal event로 취급한다.
- `updatedAt`은 과거로 되돌아가지 않게 한다.
- 백필은 제거하지 않고 운영 복구용으로 유지한다.

## 비목표

- Kafka, RabbitMQ, SQS 같은 message broker를 도입하지 않는다.
- Debezium 같은 CDC 구조로 전환하지 않는다.
- `Doc` aggregate version을 새로 도입하지 않는다.
- consumer별 checkpoint 테이블을 새로 만들지 않는다.
- 문서 목록 API 응답 계약을 바꾸지 않는다.
- 기존 백필 기능을 삭제하지 않는다.

## 선택한 설계

### 1. Wakeup 의미 변경

현재 의미:

```text
이 outboxId를 지금 처리한다.
```

변경 후 의미:

```text
새 이벤트가 생겼으니 worker를 깨워 OPEN 이벤트를 순서대로 처리한다.
```

`DomainEventOutboxWakeUpListener`는 `relay.run(event.outboxId())` 대신 `relay.run()`을 호출한다. `DomainEventOutboxRelay.run(Long outboxId)`는 호환성을 위해 남기되, 내부에서 `run()`으로 위임하고 deprecated 처리한다.

이렇게 하면 wakeup은 latency optimization으로 남고, 실제 처리 순서는 `createdAt ASC, id ASC`로 OPEN 이벤트를 조회하는 relay에 맡긴다. `createdAt`이 같은 row는 DB 반환 순서에 기대지 않고 id를 tie-breaker로 사용한다.

### 2. Projection marker 분리

`DocListReadModel`의 단일 `lastProjectedEventId`는 projection 판단 기준으로 부적합하다. 이벤트가 서로 다른 필드를 갱신하기 때문이다.

필드 그룹별 projection marker를 둔다.

```java
private Long titleProjectedEventId;
private Long activityProjectedEventId;
private Long thumbnailProjectedEventId;
private Long deletedEventId;
```

`lastProjectedEventId`는 기존 Mongo 문서 호환성과 관찰용으로 유지할 수 있지만, projection 적용 여부의 핵심 판단은 필드별 marker가 담당한다.

기존 Mongo Read Model 문서에는 새 marker 필드가 없을 수 있다. 이 경우 marker가 null이고 `lastProjectedEventId`가 존재하면 legacy 문서로 보고 `lastProjectedEventId`를 해당 필드의 기준선으로 사용한다. 반면 backfill로 새로 만든 문서는 `lastProjectedEventId`도 null이므로 이후 이벤트를 필드별로 받을 수 있다.

### 3. 이벤트 적용 규칙

`DOC_CREATED`

- Read Model이 이미 있으면 멱등하게 무시한다.
- 새 Read Model 생성 시 title, activity, thumbnail marker를 생성 이벤트 id로 초기화한다.
- 삭제 marker는 null로 둔다.

`DOC_TITLE_CHANGED`

- 문서가 삭제 상태면 무시한다.
- `titleProjectedEventId`보다 최신 이벤트만 title을 갱신한다.
- `updatedAt`은 현재 값과 payload 값 중 더 최신 값으로 유지한다.

`DOC_ACTIVITY_CHANGED`

- 문서가 삭제 상태면 무시한다.
- `activityProjectedEventId`보다 최신 이벤트만 `recentSaveId`를 갱신한다.
- `updatedAt`은 현재 값과 payload 값 중 더 최신 값으로 유지한다.

`DOC_THUMBNAIL_CHANGED`

- 문서가 삭제 상태면 무시한다.
- `thumbnailProjectedEventId`보다 최신 이벤트만 썸네일 필드를 갱신한다.

`DOC_DELETED`

- `deletedEventId`보다 최신 이벤트면 `deleted=true`로 변경한다.
- 삭제 marker가 아직 없다면 다른 필드 marker보다 오래된 삭제 이벤트라도 terminal event로 반영한다.
- 삭제 이후 title, activity, thumbnail 이벤트는 문서를 되살릴 수 없다.

핵심 정책은 다음이다.

```text
같은 필드의 오래된 이벤트는 막는다.
다른 필드의 누락 이벤트는 제한적으로 허용한다.
삭제 이후 이벤트는 문서를 되살리지 못한다.
updatedAt은 단조 증가한다.
```

## 검토한 대안

### Wakeup 제거

스케줄러만 사용하면 순서 문제는 줄지만, Read Model 반영 지연이 커진다. 사용자 요청 직후 목록이 오래 stale하게 보일 수 있다. 현재 프로젝트에서는 wakeup을 제거하기보다 의미를 낮추는 쪽이 낫다.

### Aggregate version 도입

정석적으로는 `Doc` aggregate version을 두고 이벤트에 `aggregateVersion`을 저장하는 방법이 더 좋다. 그러나 이 방식은 테이블 변경, 이벤트 저장 구조 변경, aggregate별 순서 처리 정책까지 필요하다. 현재 P1 범위에는 과하다.

### Consumer checkpoint 분리

여러 consumer가 생긴다면 `consumer_name`, `last_processed_event_id` 같은 checkpoint 저장소가 필요하다. 하지만 현재 consumer는 `DocListProjector` 하나다. 지금 단계에서는 YAGNI다.

## 테스트 전략

단위 테스트는 `DocListProjectorUnitTest`를 중심으로 작성한다.

필수 테스트:

- 썸네일 이벤트가 먼저 반영된 뒤 더 오래된 activity 이벤트가 도착해도 `recentSaveId`는 갱신된다.
- 같은 activity 필드의 오래된 이벤트는 `recentSaveId`와 `updatedAt`을 되돌리지 않는다.
- title 이벤트 이후 더 오래된 activity 이벤트가 도착해도 `updatedAt`은 과거로 되돌아가지 않는다.
- 삭제 이벤트가 반영된 뒤 더 오래된 title/activity/thumbnail 이벤트는 문서를 되살리지 않는다.
- `DOC_CREATED`는 새 marker를 초기화한다.

통합 테스트는 `DocListProjectorIntegrationTest` 또는 `DomainEventOutboxRelayIntegrationTest`에서 확인한다.

- `run(outboxId)`를 호출해도 특정 row만 처리하지 않고 OPEN 이벤트를 생성 순서대로 처리한다.
- 생성 이벤트와 변경 이벤트가 OPEN 상태로 같이 있을 때, 변경 이벤트 id로 wakeup되어도 생성 이벤트가 먼저 처리된다.

## 백필 정책

백필은 이미 수행됐더라도 삭제하지 않는다. 백필은 일반 런타임 기능이 아니라 운영 복구용 기능이다.

사용 목적:

- Read Model 컬렉션 재생성
- projection 버그 수정 후 재구축
- Mongo 데이터 손상 복구

추후 문서에는 다음처럼 명시한다.

```text
Doc List Read Model backfill은 일반 요청 흐름에서 사용하지 않는다.
운영 복구 또는 projection 재생성 상황에서 전용 프로필로 실행한다.
```

## 리스크

이번 설계는 aggregate별 완전한 순서 보장을 제공하지 않는다. 여러 서버가 동시에 relay를 실행하면 여전히 완벽한 per-aggregate ordering은 보장되지 않는다. 대신 projector가 중복과 역순을 견디도록 만들어 현재 구조에서 발생 가능한 손상을 줄인다.

진짜 범용 이벤트 아키텍처가 필요해지는 시점에는 aggregate version, consumer checkpoint, broker 또는 CDC 도입을 별도 설계해야 한다.

## 완료 기준

- Wakeup은 특정 outboxId 직접 처리 명령이 아니게 된다.
- Relay는 OPEN 이벤트를 생성 순서대로 처리한다.
- Projector는 필드별 멱등성을 가진다.
- 삭제 이후 오래된 이벤트가 문서를 되살리지 못한다.
- `updatedAt`은 과거로 되돌아가지 않는다.
- 테스트가 현재 문제가 실제로 재현되고 수정 후 통과함을 보여준다.
- AI는 commit과 push를 수행하지 않는다.

## 구현 결과

- `DomainEventOutboxWakeUpListener`는 특정 outbox row를 처리하지 않고 relay 실행만 요청하도록 변경했다.
- `DomainEventOutboxRelay.run(Long outboxId)`는 호환성을 위해 남기되 `run()`으로 위임하고 deprecated 처리했다.
- Domain Event Outbox 조회 순서는 `createdAt ASC, id ASC`로 변경해 `createdAt` 동률에서도 결정적인 처리 순서를 갖도록 했다.
- `DocListReadModel`은 title, activity, thumbnail, deleted 필드 그룹별 projection marker를 갖도록 변경했다.
- marker가 없는 기존 Mongo 문서는 `lastProjectedEventId`를 기준선으로 사용해 오래된 이벤트가 기존 값을 덮어쓰지 않도록 했다.
- 삭제 이벤트는 terminal event이므로 삭제 marker가 없는 경우 `lastProjectedEventId` fallback을 사용하지 않도록 분리했다.
- title, activity, thumbnail 변경 이벤트는 삭제된 문서를 되살리지 못하도록 terminal guard를 적용했다.
- title/activity 이벤트의 `updatedAt`은 현재 값과 payload 값 중 더 최신 값을 유지하도록 변경했다.
- `DOC_CREATED`는 새 Read Model 생성 시 필드별 marker를 생성 이벤트 id로 초기화한다.
- backfill로 생성한 Read Model은 marker를 null로 유지해 운영 복구 후 이벤트를 받을 수 있게 둔다.
- 백필 기능은 삭제하지 않고 운영 복구용 기능으로 유지한다.

## 검증 결과

- `bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest`
- `bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest --tests io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelayIntegrationTest`
- `bash ./gradlew test --tests io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelayIntegrationTest`
- `bash ./gradlew compileJava compileTestJava`
