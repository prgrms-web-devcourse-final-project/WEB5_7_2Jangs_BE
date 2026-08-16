package io.ejangs.docsa.domain.doc.readmodel.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocActivityChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocDeletedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocThumbnailChangedPayload;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocTitleChangedPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.global.outbox.event.dto.DomainEventMessage;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocListProjectorUnitTest {

    @Mock
    private DocListReadModelRepository docListReadModelRepository;

    private ObjectMapper objectMapper;
    private DocListProjector docListProjector;

    private final Long docId = 1L;
    private final Long userId = 2L;
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    private final LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        docListProjector = new DocListProjector(docListReadModelRepository, objectMapper);
    }

    @Test
    @DisplayName("DOC_CREATED 이벤트를 문서 목록 read model로 생성한다")
    void project_success_docCreated() throws Exception {
        DocCreatedPayload payload = createdPayload();
        when(docListReadModelRepository.existsById(docId)).thenReturn(false);

        docListProjector.project(message(1L, DomainEventType.DOC_CREATED, payload));

        ArgumentCaptor<DocListReadModel> captor = ArgumentCaptor.forClass(DocListReadModel.class);
        verify(docListReadModelRepository).save(captor.capture());

        DocListReadModel saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(docId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTitle()).isEqualTo("초기 제목");
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(saved.getRecentSaveId()).isEqualTo(10L);
        assertThat(saved.getThumbnailObjectKey()).isEqualTo("thumbnail-1");
        assertThat(saved.getThumbnailStatus()).isEqualTo(ThumbnailStatus.READY);
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getLastProjectedEventId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DOC_CREATED 이벤트는 read model이 이미 존재하면 멱등하게 무시한다")
    void project_ignore_docCreatedWhenReadModelAlreadyExists() throws Exception {
        DocCreatedPayload payload = createdPayload();
        when(docListReadModelRepository.existsById(docId)).thenReturn(true);

        docListProjector.project(message(1L, DomainEventType.DOC_CREATED, payload));

        verify(docListReadModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("DOC_TITLE_CHANGED 이벤트를 문서 목록 read model에 반영한다")
    void project_success_docTitleChanged() throws Exception {
        DocListReadModel model = existingModel(1L);
        LocalDateTime titleUpdatedAt = LocalDateTime.of(2026, 1, 3, 10, 0);
        DocTitleChangedPayload payload = new DocTitleChangedPayload(docId, "변경 제목", titleUpdatedAt);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(2L, DomainEventType.DOC_TITLE_CHANGED, payload));

        verify(docListReadModelRepository).save(model);
        assertThat(model.getTitle()).isEqualTo("변경 제목");
        assertThat(model.getUpdatedAt()).isEqualTo(titleUpdatedAt);
        assertThat(model.isDeleted()).isFalse();
        assertThat(model.getLastProjectedEventId()).isEqualTo(2L);
    }

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
                .hasMessageContaining("eventId=2")
                .hasMessageContaining(docId.toString());

        verify(docListReadModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("DOC_ACTIVITY_CHANGED 이벤트를 문서 목록 read model에 반영한다")
    void project_success_docActivityChanged() throws Exception {
        DocListReadModel model = existingModel(1L);
        LocalDateTime activityUpdatedAt = LocalDateTime.of(2026, 1, 4, 10, 0);
        DocActivityChangedPayload payload = new DocActivityChangedPayload(docId, 20L, activityUpdatedAt);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(2L, DomainEventType.DOC_ACTIVITY_CHANGED, payload));

        verify(docListReadModelRepository).save(model);
        assertThat(model.getRecentSaveId()).isEqualTo(20L);
        assertThat(model.getUpdatedAt()).isEqualTo(activityUpdatedAt);
        assertThat(model.isDeleted()).isFalse();
        assertThat(model.getLastProjectedEventId()).isEqualTo(2L);
    }

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
                .hasMessageContaining("eventId=2")
                .hasMessageContaining(docId.toString());

        verify(docListReadModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("DOC_THUMBNAIL_CHANGED 이벤트를 문서 목록 read model에 반영한다")
    void project_success_docThumbnailChanged() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocThumbnailChangedPayload payload =
                new DocThumbnailChangedPayload(docId, "thumbnail-2", ThumbnailStatus.PENDING);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(2L, DomainEventType.DOC_THUMBNAIL_CHANGED, payload));

        verify(docListReadModelRepository).save(model);
        assertThat(model.getThumbnailObjectKey()).isEqualTo("thumbnail-2");
        assertThat(model.getThumbnailStatus()).isEqualTo(ThumbnailStatus.PENDING);
        assertThat(model.getLastProjectedEventId()).isEqualTo(2L);
    }

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
                .hasMessageContaining("eventId=2")
                .hasMessageContaining(docId.toString());

        verify(docListReadModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("DOC_DELETED 이벤트를 문서 목록 read model에 반영한다")
    void project_success_docDeleted() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocDeletedPayload payload = new DocDeletedPayload(docId);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(2L, DomainEventType.DOC_DELETED, payload));

        verify(docListReadModelRepository).save(model);
        assertThat(model.isDeleted()).isTrue();
        assertThat(model.getLastProjectedEventId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("DOC_DELETED 이벤트는 read model이 없으면 재시도 대상 예외를 던진다")
    void project_fail_docDeletedWhenReadModelMissing() throws Exception {
        DocDeletedPayload payload = new DocDeletedPayload(docId);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> docListProjector.project(message(2L, DomainEventType.DOC_DELETED, payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Doc list read model is missing")
                .hasMessageContaining("DOC_DELETED")
                .hasMessageContaining("eventId=2")
                .hasMessageContaining(docId.toString());

        verify(docListReadModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 처리한 삭제 eventId 이하의 삭제 이벤트는 무시한다")
    void project_ignore_alreadyProjectedDeleteEvent() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocDeletedPayload payload = new DocDeletedPayload(docId);
        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(10L, DomainEventType.DOC_DELETED, payload));
        docListProjector.project(message(9L, DomainEventType.DOC_DELETED, payload));

        verify(docListReadModelRepository, times(1)).save(model);
        assertThat(model.isDeleted()).isTrue();
        assertThat(model.getLastProjectedEventId()).isEqualTo(10L);
    }

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

    @Test
    @DisplayName("삭제 이후 오래된 activity 이벤트는 문서를 되살리지 않는다")
    void project_ignoreOlderActivityEventAfterDelete() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocDeletedPayload deletedPayload = new DocDeletedPayload(docId);
        DocActivityChangedPayload activityPayload =
                new DocActivityChangedPayload(docId, 20L, LocalDateTime.of(2026, 1, 4, 10, 0));

        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(20L, DomainEventType.DOC_DELETED, deletedPayload));
        docListProjector.project(message(19L, DomainEventType.DOC_ACTIVITY_CHANGED, activityPayload));

        assertThat(model.isDeleted()).isTrue();
        assertThat(model.getRecentSaveId()).isEqualTo(10L);
        assertThat(model.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("삭제 이후 오래된 thumbnail 이벤트는 문서를 되살리지 않는다")
    void project_ignoreOlderThumbnailEventAfterDelete() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocDeletedPayload deletedPayload = new DocDeletedPayload(docId);
        DocThumbnailChangedPayload thumbnailPayload =
                new DocThumbnailChangedPayload(docId, "thumbnail-2", ThumbnailStatus.READY);

        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(20L, DomainEventType.DOC_DELETED, deletedPayload));
        docListProjector.project(message(19L, DomainEventType.DOC_THUMBNAIL_CHANGED, thumbnailPayload));

        assertThat(model.isDeleted()).isTrue();
        assertThat(model.getThumbnailObjectKey()).isEqualTo("thumbnail-1");
        assertThat(model.getThumbnailStatus()).isEqualTo(ThumbnailStatus.READY);
    }

    @Test
    @DisplayName("삭제 marker가 없으면 다른 필드보다 오래된 삭제 이벤트도 terminal로 반영한다")
    void project_applyOlderDeleteEventWhenDeleteFieldWasNotProjected() throws Exception {
        DocListReadModel model = existingModel(1L);
        DocThumbnailChangedPayload thumbnailPayload =
                new DocThumbnailChangedPayload(docId, "thumbnail-2", ThumbnailStatus.READY);
        DocDeletedPayload deletedPayload = new DocDeletedPayload(docId);

        when(docListReadModelRepository.findById(docId)).thenReturn(Optional.of(model));

        docListProjector.project(message(21L, DomainEventType.DOC_THUMBNAIL_CHANGED, thumbnailPayload));
        docListProjector.project(message(20L, DomainEventType.DOC_DELETED, deletedPayload));

        assertThat(model.isDeleted()).isTrue();
        assertThat(model.getLastProjectedEventId()).isEqualTo(21L);
    }

    private DocListReadModel existingModel(Long eventId) {
        return DocListReadModel.create(createdPayload(), eventId);
    }

    private DocCreatedPayload createdPayload() {
        return new DocCreatedPayload(
                docId,
                userId,
                "초기 제목",
                createdAt,
                updatedAt,
                10L,
                "thumbnail-1",
                ThumbnailStatus.READY
        );
    }

    private DomainEventMessage message(
            Long eventId,
            DomainEventType eventType,
            Object payload
    ) throws JsonProcessingException {
        return new DomainEventMessage(
                eventId,
                eventType,
                AggregateType.DOC,
                docId.toString(),
                objectMapper.writeValueAsString(payload),
                LocalDateTime.now()
        );
    }
}
