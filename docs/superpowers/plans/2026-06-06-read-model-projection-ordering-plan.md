# Read Model Projection 순서 안정화 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 저장소 `AGENTS.md`가 우선이므로 AI는 commit, push, merge, rebase를 수행하지 않는다.

**Goal:** 범용 확장 가능한 Domain Event Outbox 구조는 유지하면서, 첫 consumer인 Doc List Read Model projection이 wakeup 순서 역전, 중복 이벤트, 삭제 이후 오래된 이벤트를 견딜 수 있게 만든다.

**Architecture:** Wakeup은 특정 outbox row 처리 명령이 아니라 relay를 깨우는 신호로만 사용한다. Relay는 OPEN 이벤트를 생성 순서대로 처리하고, 현재 consumer인 `DocListReadModel`은 필드별 projection marker와 삭제 terminal 정책으로 out-of-order 이벤트를 방어한다. 추후 다른 consumer가 추가될 수 있으므로 outbox 저장 구조 자체는 Projection 전용으로 축소하지 않는다.

**Tech Stack:** Spring Boot, Spring Data JPA, Spring Data MongoDB, Domain Event Outbox, JUnit 5, Mockito, AssertJ

---

## 파일 구조

- Modify: `src/main/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxWakeUpListener.java`
  - wakeup 수신 시 `relay.run()`을 호출한다.
- Modify: `src/main/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxRelay.java`
  - `run(Long outboxId)`를 `run()` 위임 메서드로 낮추고 deprecated 처리한다.
- Modify: `src/main/java/io/ejangs/docsa/domain/doc/readmodel/document/DocListReadModel.java`
  - 필드별 projection marker를 추가한다.
  - 삭제 terminal guard와 `updatedAt` 단조 증가 규칙을 추가한다.
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`
  - 역순 이벤트, 같은 필드 오래된 이벤트, 삭제 terminal, `updatedAt` 단조 증가 테스트를 추가한다.
  - 기존 `lastProjectedEventId` 중심 assertion을 새 marker 정책에 맞게 수정한다.
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`
  - `run(outboxId)` 호출이 특정 row만 처리하지 않고 OPEN 이벤트를 생성 순서대로 처리하는지 검증한다.
- Modify: `src/test/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxRelayIntegrationTest.java`
  - 필요하면 relay public behavior 테스트를 `run()` 중심으로 정리한다.

## Task 1: Wakeup 정책 실패 테스트 작성

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 추가**

`relayProjector_success_retryAfterReadModelCreated()`와 별도로 다음 테스트를 추가한다. 이 테스트는 현재 코드에서 실패해야 한다. 현재 `run(titleOutbox.getId())`가 title outbox만 먼저 처리해 retry가 발생하기 때문이다.

```java
@Test
@DisplayName("run(outboxId)는 특정 row만 처리하지 않고 OPEN 이벤트를 생성 순서대로 처리한다")
void relayProjector_processOpenEventsInCreatedOrderWhenWakeUpWithSpecificId() throws Exception {
    LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    LocalDateTime initialUpdatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
    LocalDateTime titleUpdatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);

    DocCreatedPayload createdPayload = new DocCreatedPayload(
            docId,
            2L,
            "초기 제목",
            createdAt,
            initialUpdatedAt,
            10L,
            "thumbnail-1",
            ThumbnailStatus.READY
    );
    DomainEventOutbox createdOutbox = domainEventOutboxRepository.saveAndFlush(
            DomainEventOutbox.open(
                    DomainEventType.DOC_CREATED,
                    AggregateType.DOC,
                    docId.toString(),
                    objectMapper.writeValueAsString(createdPayload)
            )
    );
    jdbcTemplate.update(
            "update domain_event_outbox set payload = ? format json where id = ?",
            objectMapper.writeValueAsString(createdPayload),
            createdOutbox.getId()
    );

    DocTitleChangedPayload titlePayload = new DocTitleChangedPayload(docId, "변경 제목", titleUpdatedAt);
    DomainEventOutbox titleOutbox = domainEventOutboxRepository.saveAndFlush(
            DomainEventOutbox.open(
                    DomainEventType.DOC_TITLE_CHANGED,
                    AggregateType.DOC,
                    docId.toString(),
                    objectMapper.writeValueAsString(titlePayload)
            )
    );
    jdbcTemplate.update(
            "update domain_event_outbox set payload = ? format json where id = ?",
            objectMapper.writeValueAsString(titlePayload),
            titleOutbox.getId()
    );

    domainEventOutboxRelay.run(titleOutbox.getId());

    DomainEventOutbox doneCreated = domainEventOutboxRepository.findById(createdOutbox.getId()).orElseThrow();
    DomainEventOutbox doneTitle = domainEventOutboxRepository.findById(titleOutbox.getId()).orElseThrow();
    DocListReadModel readModel = docListReadModelRepository.findById(docId).orElseThrow();

    assertThat(doneCreated.getStatus()).isEqualTo(OutboxStatus.DONE);
    assertThat(doneTitle.getStatus()).isEqualTo(OutboxStatus.DONE);
    assertThat(doneTitle.getRetryCount()).isEqualTo(0);
    assertThat(readModel.getTitle()).isEqualTo("변경 제목");
    assertThat(readModel.getUpdatedAt()).isEqualTo(titleUpdatedAt);
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest
```

Expected:

- 새 테스트가 실패한다.
- 실패 이유는 title event가 먼저 처리되어 read model missing retry가 발생하거나, title outbox가 바로 DONE이 되지 않는 형태여야 한다.

## Task 2: Wakeup을 relay 깨우기 신호로 변경

**Files:**
- Modify: `src/main/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxWakeUpListener.java`
- Modify: `src/main/java/io/ejangs/docsa/global/outbox/event/app/DomainEventOutboxRelay.java`

- [ ] **Step 1: Listener 수정**

`DomainEventOutboxWakeUpListener.handle()`을 다음처럼 바꾼다.

```java
@Async(AsyncConfig.OUTBOX_WAKE_UP_EXECUTOR)
@TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
)
public void handle(DomainEventOutboxWakeUpEvent event) {
    relay.run();
}
```

- [ ] **Step 2: `run(Long outboxId)` 호환 wrapper로 변경**

`DomainEventOutboxRelay.run(Long outboxId)`를 다음처럼 바꾼다.

```java
@Deprecated
public void run(Long outboxId) {
    run();
}
```

이 메서드는 기존 테스트나 호출부 호환을 위해 남긴다. 실제 의미는 특정 row 처리 명령이 아니라 relay 실행 요청이다.

- [ ] **Step 3: 통합 테스트 통과 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest
```

Expected:

- Task 1에서 추가한 테스트가 통과한다.
- 기존 `relayProjector_success_retryAfterReadModelCreated()`는 기대가 달라질 수 있으므로 다음 Task에서 새 정책에 맞게 정리한다.

## Task 3: Projection marker 실패 테스트 작성

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`

- [ ] **Step 1: 독립 필드의 오래된 이벤트 허용 테스트 추가**

```java
@Test
@DisplayName("다른 필드의 오래된 이벤트는 누락된 projection이면 반영한다")
void project_applyOlderActivityEventWhenActivityFieldWasNotProjected() throws Exception {
    DocListReadModel model = existingModel(1L);
    DocThumbnailChangedPayload thumbnailPayload =
            new DocThumbnailChangedPayload(docId, "thumbnail-2", ThumbnailStatus.READY);
    DocActivityChangedPayload activityPayload =
            new DocActivityChangedPayload(docId, 20L, LocalDateTime.of(2026, 1, 4, 10, 0));

    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));
    docListProjector.project(message(10L, DomainEventType.DOC_THUMBNAIL_CHANGED, thumbnailPayload));

    docListProjector.project(message(9L, DomainEventType.DOC_ACTIVITY_CHANGED, activityPayload));

    assertThat(model.getThumbnailObjectKey()).isEqualTo("thumbnail-2");
    assertThat(model.getRecentSaveId()).isEqualTo(20L);
    assertThat(model.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 4, 10, 0));
}
```

- [ ] **Step 2: 같은 필드의 오래된 이벤트 무시 테스트 추가**

```java
@Test
@DisplayName("같은 activity 필드의 오래된 이벤트는 recentSaveId를 되돌리지 않는다")
void project_ignoreOlderActivityEventWhenActivityFieldAlreadyProjected() throws Exception {
    DocListReadModel model = existingModel(1L);
    DocActivityChangedPayload latestPayload =
            new DocActivityChangedPayload(docId, 30L, LocalDateTime.of(2026, 1, 5, 10, 0));
    DocActivityChangedPayload olderPayload =
            new DocActivityChangedPayload(docId, 20L, LocalDateTime.of(2026, 1, 4, 10, 0));

    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));
    docListProjector.project(message(12L, DomainEventType.DOC_ACTIVITY_CHANGED, latestPayload));
    docListProjector.project(message(11L, DomainEventType.DOC_ACTIVITY_CHANGED, olderPayload));

    assertThat(model.getRecentSaveId()).isEqualTo(30L);
    assertThat(model.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 5, 10, 0));
}
```

- [ ] **Step 3: `updatedAt` 단조 증가 테스트 추가**

```java
@Test
@DisplayName("오래된 독립 이벤트가 나중에 반영되어도 updatedAt은 과거로 되돌아가지 않는다")
void project_keepUpdatedAtMonotonicWhenOlderIndependentEventArrives() throws Exception {
    DocListReadModel model = existingModel(1L);
    DocTitleChangedPayload titlePayload =
            new DocTitleChangedPayload(docId, "변경 제목", LocalDateTime.of(2026, 1, 5, 10, 0));
    DocActivityChangedPayload activityPayload =
            new DocActivityChangedPayload(docId, 20L, LocalDateTime.of(2026, 1, 4, 10, 0));

    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));
    docListProjector.project(message(12L, DomainEventType.DOC_TITLE_CHANGED, titlePayload));
    docListProjector.project(message(11L, DomainEventType.DOC_ACTIVITY_CHANGED, activityPayload));

    assertThat(model.getTitle()).isEqualTo("변경 제목");
    assertThat(model.getRecentSaveId()).isEqualTo(20L);
    assertThat(model.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 5, 10, 0));
}
```

- [ ] **Step 4: 삭제 terminal 테스트 추가**

```java
@Test
@DisplayName("삭제 이후 오래된 title 이벤트는 문서를 되살리지 않는다")
void project_ignoreOlderTitleEventAfterDelete() throws Exception {
    DocListReadModel model = existingModel(1L);
    DocDeletedPayload deletedPayload = new DocDeletedPayload(docId);
    DocTitleChangedPayload titlePayload =
            new DocTitleChangedPayload(docId, "삭제 전 변경 제목", LocalDateTime.of(2026, 1, 4, 10, 0));

    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));
    docListProjector.project(message(20L, DomainEventType.DOC_DELETED, deletedPayload));
    docListProjector.project(message(19L, DomainEventType.DOC_TITLE_CHANGED, titlePayload));

    assertThat(model.isDeleted()).isTrue();
    assertThat(model.getTitle()).isEqualTo("초기 제목");
}
```

- [ ] **Step 5: 실패 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest
```

Expected:

- Step 1 테스트는 현재 단일 `lastProjectedEventId` 때문에 activity가 무시되어 실패한다.
- Step 3 테스트는 `updatedAt`이 과거로 돌아가거나 activity가 무시되어 실패한다.
- Step 4 테스트는 현재 `changeTitle()`이 `deleted=false`를 세팅하므로 실패한다.

## Task 4: `DocListReadModel` projection 정책 구현

**Files:**
- Modify: `src/main/java/io/ejangs/docsa/domain/doc/readmodel/document/DocListReadModel.java`

- [ ] **Step 1: 필드별 marker 추가**

`lastProjectedEventId` 아래에 다음 필드를 추가한다.

```java
private Long titleProjectedEventId;
private Long activityProjectedEventId;
private Long thumbnailProjectedEventId;
private Long deletedEventId;
```

- [ ] **Step 2: 생성 projection marker 초기화**

`create()`에서 다음 값을 설정한다.

```java
model.lastProjectedEventId = eventId;
model.titleProjectedEventId = eventId;
model.activityProjectedEventId = eventId;
model.thumbnailProjectedEventId = eventId;
model.deletedEventId = null;
```

- [ ] **Step 3: backfill marker는 null 유지**

`backfill()`에서는 marker를 null로 둔다. 백필된 문서는 이후 들어오는 이벤트가 각 필드별로 처음 처리될 수 있어야 한다.

- [ ] **Step 4: 공통 helper 추가**

클래스 하단에 다음 helper를 추가한다.

```java
private boolean isDeletedTerminal() {
    return this.deleted;
}

private boolean isAlreadyProjected(Long projectedEventId, Long eventId) {
    return projectedEventId != null && projectedEventId >= eventId;
}

private void touchLastProjectedEventId(Long eventId) {
    if (this.lastProjectedEventId == null || this.lastProjectedEventId < eventId) {
        this.lastProjectedEventId = eventId;
    }
}

private LocalDateTime maxUpdatedAt(LocalDateTime nextUpdatedAt) {
    if (this.updatedAt == null) {
        return nextUpdatedAt;
    }
    if (nextUpdatedAt == null) {
        return this.updatedAt;
    }
    return this.updatedAt.isAfter(nextUpdatedAt) ? this.updatedAt : nextUpdatedAt;
}
```

- [ ] **Step 5: `changeTitle()` 수정**

```java
public boolean changeTitle(DocTitleChangedPayload payload, Long eventId) {
    if (isDeletedTerminal() || isAlreadyProjected(this.titleProjectedEventId, eventId)) {
        return false;
    }

    this.title = payload.title();
    this.updatedAt = maxUpdatedAt(payload.updatedAt());
    this.titleProjectedEventId = eventId;
    touchLastProjectedEventId(eventId);
    return true;
}
```

- [ ] **Step 6: `changeActivity()` 수정**

```java
public boolean changeActivity(DocActivityChangedPayload payload, Long eventId) {
    if (isDeletedTerminal() || isAlreadyProjected(this.activityProjectedEventId, eventId)) {
        return false;
    }

    this.recentSaveId = payload.recentSaveId();
    this.updatedAt = maxUpdatedAt(payload.updatedAt());
    this.activityProjectedEventId = eventId;
    touchLastProjectedEventId(eventId);
    return true;
}
```

- [ ] **Step 7: `changeThumbnail()` 수정**

```java
public boolean changeThumbnail(DocThumbnailChangedPayload payload, Long eventId) {
    if (isDeletedTerminal() || isAlreadyProjected(this.thumbnailProjectedEventId, eventId)) {
        return false;
    }

    this.thumbnailObjectKey = payload.thumbnailObjectKey();
    this.thumbnailStatus = payload.thumbnailStatus();
    this.thumbnailProjectedEventId = eventId;
    touchLastProjectedEventId(eventId);
    return true;
}
```

- [ ] **Step 8: `markDeleted()` 수정**

```java
public boolean markDeleted(Long eventId) {
    if (isAlreadyProjected(this.deletedEventId, eventId)) {
        return false;
    }

    this.deleted = true;
    this.deletedEventId = eventId;
    touchLastProjectedEventId(eventId);
    return true;
}
```

- [ ] **Step 9: 기존 `isAlreadyProjected(Long eventId)` 제거**

단일 `lastProjectedEventId`만 보는 기존 helper는 삭제한다.

- [ ] **Step 10: 단위 테스트 통과 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest
```

Expected:

- `DocListProjectorUnitTest` 전체 통과

## Task 5: 기존 테스트를 새 정책에 맞게 정리

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`

- [ ] **Step 1: `lastProjectedEventId` 단일 guard 테스트 수정**

기존 테스트명:

```java
@DisplayName("이미 처리한 eventId 이하의 이벤트는 무시한다")
```

이 테스트는 삭제 이벤트 또는 같은 필드 이벤트 기준으로 바꾼다. 예를 들어 삭제 이벤트 중복 방지 테스트로 수정한다.

```java
@Test
@DisplayName("이미 처리한 삭제 eventId 이하의 삭제 이벤트는 무시한다")
void project_ignore_alreadyProjectedDeleteEvent() throws Exception {
    DocListReadModel model = existingModel(1L);
    DocDeletedPayload payload = new DocDeletedPayload(docId);
    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

    docListProjector.project(message(10L, DomainEventType.DOC_DELETED, payload));
    docListProjector.project(message(9L, DomainEventType.DOC_DELETED, payload));

    verify(docListReadModelRepository).save(model);
    assertThat(model.isDeleted()).isTrue();
}
```

- [ ] **Step 2: `relayProjector_success_retryAfterReadModelCreated()` 정책 변경**

`run(Long outboxId)`가 `run()`으로 위임되면 title event가 먼저 retry되지 않는다. 기존 테스트는 다음 둘 중 하나로 정리한다.

추천: 기존 테스트를 삭제하지 말고, “수동으로 title을 먼저 claim해야 retry된다”는 테스트로 바꾸지 않는다. 새 wakeup 정책에서는 그 시나리오를 public behavior로 유지할 필요가 약하다. 대신 Task 1의 테스트가 새 정책을 대표하게 둔다.

- [ ] **Step 3: 통합 테스트 통과 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest
```

Expected:

- `DocListProjectorIntegrationTest` 전체 통과

## Task 6: 문서와 백필 경계 정리

**Files:**
- Modify: `docs/superpowers/specs/2026-06-06-read-model-projection-ordering-design.md`
- Optionally Modify: `ai/ai-assisted-development-workflow.md`

- [ ] **Step 1: 설계 문서에 수행 결과 갱신**

구현 후 설계 문서 하단에 다음 내용을 추가한다.

```markdown
## 구현 결과

- Wakeup은 특정 outbox row 처리 명령이 아니라 relay 실행 신호로 변경했다.
- `DocListReadModel`은 필드별 projection marker로 중복과 역순 이벤트를 방어한다.
- 삭제 이벤트는 terminal event로 처리한다.
- `updatedAt`은 과거로 되돌아가지 않도록 max 정책을 적용했다.
- 백필은 일반 런타임 기능이 아니라 운영 복구용 기능으로 유지한다.
```

- [ ] **Step 2: 필요하면 AI workflow 문서에는 기록만 남긴다**

이 변경은 이력서/포트폴리오에 “AI 활용” 증거로 사용할 수 있다. 단, 원본 프롬프트나 비밀 정보는 커밋하지 않는다.

## Task 7: 전체 검증

**Files:**
- No production file edits in this task.

- [ ] **Step 1: 핵심 테스트 실행**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest --tests io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelayIntegrationTest
```

Expected:

- 세 테스트 클래스 모두 통과

- [ ] **Step 2: 컴파일 검증**

Run:

```bash
bash ./gradlew compileJava compileTestJava
```

Expected:

- main/test 컴파일 성공

- [ ] **Step 3: 변경 파일 확인**

Run:

```bash
git status --short
```

Expected:

- 코드 변경 파일과 문서 파일만 표시된다.
- AI는 commit과 push를 하지 않는다.

## Self-review

- Spec coverage: wakeup 의미 변경, projection marker 분리, 삭제 terminal, `updatedAt` 단조 증가, 백필 유지 정책을 모두 task로 연결했다.
- Placeholder scan: `TBD`, `TODO`, `나중에 구현` 같은 빈 항목은 없다.
- Type consistency: 현재 코드의 class, method, record 이름을 기준으로 작성했다.
- Scope check: broker, CDC, aggregate version, consumer checkpoint는 이번 범위에서 제외했다.

## 진행 결과

- Task 1 완료: wakeup이 특정 row를 직접 처리하는 기존 문제를 실패 테스트로 재현했다.
- Task 2 완료: wakeup을 relay 실행 신호로 변경했고 통합 테스트를 통과시켰다.
- Task 3 완료: projector의 단일 `lastProjectedEventId` 문제가 독립 필드 이벤트를 누락시키는 실패 테스트를 추가했다.
- Task 4 완료: `DocListReadModel`에 필드별 projection marker, 삭제 terminal guard, `updatedAt` 단조 증가 정책을 구현했다. marker가 없는 기존 Mongo 문서는 `lastProjectedEventId`를 기준선으로 사용하는 호환 정책도 추가했다.
- Task 5 완료: 기존 테스트를 새 projection 정책에 맞게 정리했다. 삭제 이후 title, activity, thumbnail 이벤트가 모두 문서를 되살리지 못하는 테스트도 추가했다.
- Task 6 완료: 설계 문서에 구현 결과와 검증 결과를 기록했다.
- Task 7 완료: 핵심 단위 테스트, 통합 테스트, main/test 컴파일 검증을 수행했다.
- 리뷰 반영 완료: Domain Event Outbox 조회 순서를 `createdAt ASC, id ASC`로 변경하고, `createdAt` 동률 시 id 순서 dispatch 테스트를 추가했다.
- 리뷰 반영 완료: 삭제 marker가 없는 경우 다른 필드보다 오래된 삭제 이벤트라도 terminal event로 반영되도록 테스트와 구현을 보강했다.

검증 명령:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest --tests io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelayIntegrationTest
bash ./gradlew test --tests io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxRelayIntegrationTest
bash ./gradlew compileJava compileTestJava
```
