# Read Model 이벤트 재시도 설계

## 문제

Docsa는 문서 목록 응답을 MongoDB Read Model에서 조회한다. 도메인 이벤트는 RDB Outbox에 저장되고, MySQL 트랜잭션이 커밋된 뒤 비동기로 dispatch된다.

현재 `DocListProjector`는 변경 이벤트를 처리할 때 대상 Read Model이 없으면 조용히 아무 작업도 하지 않는다. 반면 `DomainEventOutboxRelay`는 dispatch 중 예외가 발생하지 않으면 outbox row를 `DONE`으로 완료 처리한다.

이 때문에 `DOC_CREATED` projection보다 변경 이벤트가 먼저 처리되면, 실제로는 Read Model에 아무것도 반영되지 않았는데 이벤트는 완료 처리되어 영구 유실될 수 있다.

## 실패 시나리오

1. 문서 생성 트랜잭션에서 `DOC_CREATED` outbox row가 저장된다.
2. 생성 이벤트가 아직 projection되기 전에 제목 변경, 저장, 썸네일 변경, 삭제 이벤트가 저장된다.
3. 비동기 wake-up 순서 때문에 나중 이벤트가 먼저 dispatch된다.
4. `DocListProjector`가 Read Model을 찾지 못하고 아무 작업도 하지 않는다.
5. relay는 dispatch 성공으로 판단해 outbox row를 `DONE` 처리한다.
6. 이후 `DOC_CREATED` 이벤트가 projection된다.
7. 먼저 처리됐던 변경 이벤트는 재시도할 수 없으므로 Read Model이 오래된 상태로 남을 수 있다.

가능한 결과:

- 문서 목록에 이전 제목이 남을 수 있다.
- 최근 저장 시각이나 최근 저장 ID가 반영되지 않을 수 있다.
- 썸네일 상태가 반영되지 않을 수 있다.
- 삭제 이벤트가 생성 projection보다 먼저 무시되면, 이후 생성 projection으로 삭제된 문서가 목록에 노출될 수 있다.

## 목표

기존 Read Model이 필요한 변경 이벤트는 대상 Read Model이 없을 때 완료 처리되면 안 된다. 기존 Domain Event Outbox의 retry 흐름을 통해 다시 처리 가능한 상태로 남겨야 한다.

## 제외 범위

- pending event buffer를 새로 만들지 않는다.
- domain event relay의 public contract를 바꾸지 않는다.
- 문서 목록 API 응답 계약을 바꾸지 않는다.
- 썸네일 `PENDING` 상태 projection 문제는 이번 변경에 포함하지 않는다.

## 추천 접근

기존 outbox retry 메커니즘을 그대로 사용한다.

`DocListProjector`가 기존 Read Model이 필요한 이벤트를 받았는데 Read Model을 찾지 못하면 예외를 던진다. `DomainEventOutboxRelay`는 이미 dispatch 예외를 잡아 `retry(outboxId, errorMessage)`를 호출하므로, 해당 이벤트는 `DONE`으로 완료되지 않고 재시도 대상이 된다.

이벤트별 동작:

- `DOC_CREATED`: 기존처럼 멱등성을 유지한다. Read Model이 이미 있으면 오류 없이 skip한다.
- `DOC_TITLE_CHANGED`: Read Model이 반드시 필요하다. 없으면 retry 대상이다.
- `DOC_ACTIVITY_CHANGED`: Read Model이 반드시 필요하다. 없으면 retry 대상이다.
- `DOC_THUMBNAIL_CHANGED`: Read Model이 반드시 필요하다. 없으면 retry 대상이다.
- `DOC_DELETED`: Read Model이 반드시 필요하다. 없으면 retry 대상이다.

## 검토한 대안

### Projection 결과를 relay에 반환

`DocListProjector.project()`가 `SUCCESS`, `SKIPPED_DUPLICATE`, `RETRYABLE_MISSING_READ_MODEL` 같은 결과를 반환하게 만들 수 있다.

예외를 제어 흐름으로 쓰지 않는 장점은 있지만, 이번 문제에 비해 dispatcher와 relay의 계약 변경이 커진다.

### Pending Event Buffer

`DOC_CREATED`보다 먼저 도착한 변경 이벤트를 별도 저장소에 보관하고, 생성 projection 이후 순서대로 재적용할 수 있다.

순서 역전을 가장 정교하게 다룰 수 있지만, 새 저장소와 replay 흐름이 필요하다. 이번 P1 수정 범위에는 과하다.

## 테스트 전략

`DocListProjector` 단위 테스트를 중심으로 검증한다.

필수 테스트:

- `DOC_CREATED`에서 Read Model이 이미 존재하면 오류 없이 멱등하게 무시한다.
- `DOC_TITLE_CHANGED`에서 Read Model이 없으면 예외가 발생한다.
- `DOC_ACTIVITY_CHANGED`에서 Read Model이 없으면 예외가 발생한다.
- `DOC_THUMBNAIL_CHANGED`에서 Read Model이 없으면 예외가 발생한다.
- `DOC_DELETED`에서 Read Model이 없으면 예외가 발생한다.
- 기존 중복 이벤트 방지 동작은 유지된다.
- 기존 정상 projection 동작은 유지된다.
- Read Model이 없는 update event outbox row가 relay 처리 후 `DONE`이 아니라 retry 가능한 상태로 남는지 확인한다.
- 먼저 실패한 update event outbox row가 `DOC_CREATED` projection 이후 재시도되어 최종 반영되는지 확인한다.

## 리스크

Read Model이 데이터 손상이나 수동 삭제로 영구적으로 없는 경우, 해당 outbox row는 max retry 이후 `FAILED`가 될 수 있다. 하지만 현재처럼 조용히 `DONE` 처리되는 것보다 낫다. `FAILED` 상태는 운영자가 감지하고 보정할 수 있기 때문이다.

## 완료 기준

- 변경 이벤트가 Read Model 없음 상황에서 조용히 성공하지 않는다.
- 기존 relay retry 흐름을 재사용한다.
- 테스트가 missing read model 경로를 증명한다.
- AI는 commit과 push를 수행하지 않는다.
