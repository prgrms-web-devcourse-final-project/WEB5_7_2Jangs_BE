# Read Model 이벤트 재시도 구현 계획

> **Agent 작업 지침:** 이 계획을 실행할 때는 `superpowers:subagent-driven-development`를 사용한다. 단, 저장소 `AGENTS.md`가 우선이므로 AI는 commit, push, merge, rebase를 수행하지 않는다. 각 단계는 체크박스 문법으로 추적한다.

**목표:** Read Model이 필요한 변경 이벤트가 생성 projection보다 먼저 도착했을 때 조용히 `DONE` 처리되지 않고 기존 Outbox retry 흐름을 타도록 수정한다.

**구조:** `DocListProjector`에서 기존 Read Model이 필요한 이벤트를 처리할 때 모델이 없으면 예외를 던진다. `DomainEventOutboxRelay`는 이미 dispatch 예외를 retry로 전환하므로 relay public contract는 바꾸지 않는다.

**기술 스택:** Spring Boot, JPA, MongoDB, Domain Event Outbox, JUnit 5, Mockito, AssertJ

---

## 파일 구조

- 수정: `src/main/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjector.java`
  - 변경 이벤트에서 Read Model을 필수로 조회하는 helper를 추가한다.
  - `DOC_TITLE_CHANGED`, `DOC_ACTIVITY_CHANGED`, `DOC_THUMBNAIL_CHANGED`, `DOC_DELETED`가 missing read model 상황에서 예외를 던지게 한다.
- 수정: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`
  - `DOC_CREATED` 멱등성 테스트를 추가한다.
  - 변경 이벤트별 missing read model 테스트를 추가한다.
- 수정: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`
  - missing read model 상황에서 relay가 outbox를 `DONE`이 아니라 retry 가능한 상태로 남기는 통합 테스트를 추가한다.

## Task 1: Projector 단위 테스트 추가

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`

- [ ] **Step 1: AssertJ 예외 검증 import 추가**

`DocListProjectorUnitTest.java` 상단 import에 다음 static import를 추가한다.

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: `DOC_CREATED` 멱등성 테스트 추가**

`project_success_docCreated()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("DOC_CREATED 이벤트는 read model이 이미 존재하면 멱등하게 무시한다")
void project_ignore_docCreatedWhenReadModelAlreadyExists() throws Exception {
    DocCreatedPayload payload = createdPayload();
    when(docListReadModelRepository.existsById(docId)).thenReturn(true);

    docListProjector.project(message(1L, DomainEventType.DOC_CREATED, payload));

    verify(docListReadModelRepository, never()).save(any());
}
```

- [ ] **Step 3: `DOC_TITLE_CHANGED` missing read model 테스트 추가**

`project_success_docTitleChanged()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("DOC_TITLE_CHANGED 이벤트는 read model이 없으면 재시도 대상 예외를 던진다")
void project_fail_docTitleChangedWhenReadModelMissing() throws Exception {
    DocTitleChangedPayload payload =
            new DocTitleChangedPayload(docId, "변경 제목", LocalDateTime.of(2026, 1, 3, 10, 0));
    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> docListProjector.project(message(2L, DomainEventType.DOC_TITLE_CHANGED, payload)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Doc list read model is missing")
            .hasMessageContaining("DOC_TITLE_CHANGED")
            .hasMessageContaining(docId.toString());

    verify(docListReadModelRepository, never()).save(any());
}
```

- [ ] **Step 4: `DOC_ACTIVITY_CHANGED` missing read model 테스트 추가**

`project_success_docActivityChanged()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("DOC_ACTIVITY_CHANGED 이벤트는 read model이 없으면 재시도 대상 예외를 던진다")
void project_fail_docActivityChangedWhenReadModelMissing() throws Exception {
    DocActivityChangedPayload payload =
            new DocActivityChangedPayload(docId, 20L, LocalDateTime.of(2026, 1, 4, 10, 0));
    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> docListProjector.project(message(2L, DomainEventType.DOC_ACTIVITY_CHANGED, payload)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Doc list read model is missing")
            .hasMessageContaining("DOC_ACTIVITY_CHANGED")
            .hasMessageContaining(docId.toString());

    verify(docListReadModelRepository, never()).save(any());
}
```

- [ ] **Step 5: `DOC_THUMBNAIL_CHANGED` missing read model 테스트 추가**

`project_success_docThumbnailChanged()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("DOC_THUMBNAIL_CHANGED 이벤트는 read model이 없으면 재시도 대상 예외를 던진다")
void project_fail_docThumbnailChangedWhenReadModelMissing() throws Exception {
    DocThumbnailChangedPayload payload =
            new DocThumbnailChangedPayload(docId, "thumbnail-2", ThumbnailStatus.PENDING);
    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> docListProjector.project(message(2L, DomainEventType.DOC_THUMBNAIL_CHANGED, payload)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Doc list read model is missing")
            .hasMessageContaining("DOC_THUMBNAIL_CHANGED")
            .hasMessageContaining(docId.toString());

    verify(docListReadModelRepository, never()).save(any());
}
```

- [ ] **Step 6: `DOC_DELETED` missing read model 테스트 추가**

`project_success_docDeleted()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("DOC_DELETED 이벤트는 read model이 없으면 재시도 대상 예외를 던진다")
void project_fail_docDeletedWhenReadModelMissing() throws Exception {
    DocDeletedPayload payload = new DocDeletedPayload(docId);
    when(docListReadModelRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> docListProjector.project(message(2L, DomainEventType.DOC_DELETED, payload)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Doc list read model is missing")
            .hasMessageContaining("DOC_DELETED")
            .hasMessageContaining(docId.toString());

    verify(docListReadModelRepository, never()).save(any());
}
```

- [ ] **Step 7: 단위 테스트 실패 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest
```

Expected:

- 새 missing read model 테스트 4개는 `IllegalStateException`이 발생하지 않아 실패한다.
- `DOC_CREATED` 멱등성 테스트는 통과해야 한다.

## Task 2: Projector missing read model 처리 구현

**Files:**
- Modify: `src/main/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjector.java`

- [ ] **Step 1: 필수 Read Model 조회 helper 추가**

`readPayload()` 아래에 다음 helper를 추가한다.

```java
private DocListReadModel getRequiredModel(Long docId, DomainEventMessage message) {
    return docListReadModelRepository.findById(docId)
            .orElseThrow(() -> new IllegalStateException(
                    "Doc list read model is missing. eventType=%s, eventId=%d, docId=%d"
                            .formatted(message.eventType(), message.eventId(), docId)
            ));
}
```

- [ ] **Step 2: `changeTitle()` 수정**

기존 `findById(...).ifPresent(...)` 블록을 다음 코드로 바꾼다.

```java
private void changeTitle(DomainEventMessage message) {
    DocTitleChangedPayload payload = readPayload(message, DocTitleChangedPayload.class);
    DocListReadModel model = getRequiredModel(payload.docId(), message);

    if (model.changeTitle(payload, message.eventId())) {
        docListReadModelRepository.save(model);
    }
}
```

- [ ] **Step 3: `changeActivity()` 수정**

기존 `findById(...).ifPresent(...)` 블록을 다음 코드로 바꾼다.

```java
private void changeActivity(DomainEventMessage message) {
    DocActivityChangedPayload payload = readPayload(message, DocActivityChangedPayload.class);
    DocListReadModel model = getRequiredModel(payload.docId(), message);

    if (model.changeActivity(payload, message.eventId())) {
        docListReadModelRepository.save(model);
    }
}
```

- [ ] **Step 4: `changeThumbnail()` 수정**

기존 `findById(...).ifPresent(...)` 블록을 다음 코드로 바꾼다.

```java
private void changeThumbnail(DomainEventMessage message) {
    DocThumbnailChangedPayload payload = readPayload(message, DocThumbnailChangedPayload.class);
    DocListReadModel model = getRequiredModel(payload.docId(), message);

    if (model.changeThumbnail(payload, message.eventId())) {
        docListReadModelRepository.save(model);
    }
}
```

- [ ] **Step 5: `delete()` 수정**

기존 `findById(...).ifPresent(...)` 블록을 다음 코드로 바꾼다.

```java
private void delete(DomainEventMessage message) {
    DocDeletedPayload payload = readPayload(message, DocDeletedPayload.class);
    DocListReadModel model = getRequiredModel(payload.docId(), message);

    if (model.markDeleted(message.eventId())) {
        docListReadModelRepository.save(model);
    }
}
```

- [ ] **Step 6: 단위 테스트 통과 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest
```

Expected:

- `DocListProjectorUnitTest` 전체 통과

## Task 3: Relay retry 통합 테스트 추가

**Files:**
- Modify: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`

- [ ] **Step 1: payload import 추가**

`DocListProjectorIntegrationTest.java` import에 다음 import를 추가한다.

```java
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
```

- [ ] **Step 2: missing read model retry 통합 테스트 추가**

`relayProjector_success_docCreated()` 아래에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("변경 이벤트가 read model보다 먼저 처리되면 outbox를 DONE 처리하지 않고 재시도 대상으로 남긴다")
void relayProjector_retry_whenReadModelMissingForUpdateEvent() throws Exception {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);
    DocTitleChangedPayload payload = new DocTitleChangedPayload(docId, "변경 제목", updatedAt);
    DomainEventOutbox outbox = domainEventOutboxRepository.saveAndFlush(
            DomainEventOutbox.open(
                    DomainEventType.DOC_TITLE_CHANGED,
                    AggregateType.DOC,
                    docId.toString(),
                    objectMapper.writeValueAsString(payload)
            )
    );
    jdbcTemplate.update(
            "update domain_event_outbox set payload = ? format json where id = ?",
            objectMapper.writeValueAsString(payload),
            outbox.getId()
    );

    domainEventOutboxRelay.run();

    DomainEventOutbox retried = domainEventOutboxRepository.findById(outbox.getId()).orElseThrow();
    Optional<DocListReadModel> readModel = docListReadModelRepository.findById(docId);

    assertThat(retried.getStatus()).isEqualTo(OutboxStatus.OPEN);
    assertThat(retried.getRetryCount()).isEqualTo(1);
    assertThat(retried.getDoneAt()).isNull();
    assertThat(retried.getLastError()).contains("Doc list read model is missing");
    assertThat(readModel).isEmpty();
}
```

- [ ] **Step 3: 통합 테스트 통과 확인**

- [ ] **Step 3: retry 이후 최종 반영 통합 테스트 추가**

같은 파일에 다음 테스트를 추가한다.

```java
@Test
@DisplayName("먼저 실패한 변경 이벤트는 read model 생성 후 재시도되어 반영된다")
void relayProjector_success_retryAfterReadModelCreated() throws Exception {
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

    DomainEventOutbox firstRetry = domainEventOutboxRepository.findById(titleOutbox.getId()).orElseThrow();
    assertThat(firstRetry.getStatus()).isEqualTo(OutboxStatus.OPEN);
    assertThat(firstRetry.getRetryCount()).isEqualTo(1);
    assertThat(firstRetry.getLastError()).contains("Doc list read model is missing");
    jdbcTemplate.update(
            "update domain_event_outbox set payload = ? format json where id = ?",
            objectMapper.writeValueAsString(titlePayload),
            titleOutbox.getId()
    );

    domainEventOutboxRelay.run(createdOutbox.getId());
    domainEventOutboxRelay.run(titleOutbox.getId());

    DomainEventOutbox doneCreated = domainEventOutboxRepository.findById(createdOutbox.getId()).orElseThrow();
    DomainEventOutbox doneTitle = domainEventOutboxRepository.findById(titleOutbox.getId()).orElseThrow();
    DocListReadModel readModel = docListReadModelRepository.findById(docId).orElseThrow();

    assertThat(doneCreated.getStatus()).isEqualTo(OutboxStatus.DONE);
    assertThat(doneTitle.getStatus())
            .as("retryCount=%s, lastError=%s", doneTitle.getRetryCount(), doneTitle.getLastError())
            .isEqualTo(OutboxStatus.DONE);
    assertThat(doneTitle.getRetryCount()).isEqualTo(1);
    assertThat(doneTitle.getLastError()).isNull();
    assertThat(readModel.getTitle()).isEqualTo("변경 제목");
    assertThat(readModel.getUpdatedAt()).isEqualTo(titleUpdatedAt);
    assertThat(readModel.getLastProjectedEventId()).isEqualTo(titleOutbox.getId());
}
```

- [ ] **Step 4: 통합 테스트 통과 확인**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest
```

Expected:

- `DocListProjectorIntegrationTest` 전체 통과

## Task 4: 회귀 검증과 최종 점검

**Files:**
- Read: `src/main/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjector.java`
- Read: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java`
- Read: `src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java`

- [ ] **Step 1: 관련 테스트 전체 실행**

Run:

```bash
bash ./gradlew test --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorUnitTest --tests io.ejangs.docsa.domain.doc.readmodel.app.DocListProjectorIntegrationTest
```

Expected:

- 두 테스트 클래스 모두 통과

- [ ] **Step 2: 변경 diff 점검**

Run:

```bash
git diff -- src/main/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjector.java src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorUnitTest.java src/test/java/io/ejangs/docsa/domain/doc/readmodel/app/DocListProjectorIntegrationTest.java
git diff --check
```

Expected:

- 변경 범위가 projector와 관련 테스트에 한정된다.
- whitespace 오류가 없다.

- [ ] **Step 3: 커밋 메시지 추천만 제시**

AI는 commit을 실행하지 않는다. 사용자에게 다음 커밋 메시지를 추천한다.

```bash
fix: Read Model 누락 시 도메인 이벤트 재시도 처리
```
