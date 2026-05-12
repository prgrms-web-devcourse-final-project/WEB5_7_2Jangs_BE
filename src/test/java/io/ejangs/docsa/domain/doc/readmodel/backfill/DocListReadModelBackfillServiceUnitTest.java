package io.ejangs.docsa.domain.doc.readmodel.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.LatestSaveIdDto;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.domain.image.entity.Image.Purpose;
import io.ejangs.docsa.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.bson.BsonInt64;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocListReadModelBackfillServiceUnitTest {

    @Mock
    private DocRepository docRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private DocListReadModelBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new DocListReadModelBackfillService(
                docRepository,
                branchRepository,
                mongoTemplate
        );
    }

    @Test
    @DisplayName("문서 목록 read model이 없으면 setOnInsert로 생성한다")
    void backfillBatch_success_insertIfAbsent() {
        User user = user(10L);
        Doc doc = doc(2L, user, "문서 2", LocalDateTime.of(2026, 1, 2, 10, 0));
        attachReadyThumbnail(doc, "thumbnail-2.webp");

        when(docRepository.findBackfillBatch(0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(doc));
        when(branchRepository.findLatestSaveIdsByDocIds(List.of(2L)))
                .thenReturn(List.of(new LatestSaveIdDto(2L, 200L)));
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, new BsonInt64(2L)));

        int count = backfillService.backfillBatch(0L, 100);

        assertThat(count).isEqualTo(1);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(DocListReadModel.class));

        Document setOnInsert = updateCaptor.getValue()
                .getUpdateObject()
                .get("$setOnInsert", Document.class);
        assertThat(setOnInsert.get("_id")).isEqualTo(2L);
        assertThat(setOnInsert.get("userId")).isEqualTo(10L);
        assertThat(setOnInsert.get("title")).isEqualTo("문서 2");
        assertThat(setOnInsert.get("recentSaveId")).isEqualTo(200L);
        assertThat(setOnInsert.get("thumbnailObjectKey")).isEqualTo("thumbnail-2.webp");
        assertThat(setOnInsert.get("thumbnailStatus")).isEqualTo(ThumbnailStatus.READY);
        assertThat(setOnInsert.get("deleted")).isEqualTo(false);
    }

    @Test
    @DisplayName("문서가 없으면 read model을 저장하지 않는다")
    void backfillBatch_emptyDocs() {
        when(docRepository.findBackfillBatch(10L, PageRequest.of(0, 100))).thenReturn(List.of());

        int count = backfillService.backfillBatch(10L, 100);

        assertThat(count).isZero();
        verify(branchRepository, never()).findLatestSaveIdsByDocIds(anyList());
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class));
    }

    @Test
    @DisplayName("read model이 이미 있으면 생성 개수에 포함하지 않는다")
    void backfillBatch_exists_notInserted() {
        User user = user(10L);
        Doc doc = doc(1L, user, "문서 1", LocalDateTime.of(2026, 1, 1, 10, 0));

        when(docRepository.findBackfillBatch(0L, PageRequest.of(0, 100))).thenReturn(List.of(doc));
        when(branchRepository.findLatestSaveIdsByDocIds(List.of(1L))).thenReturn(List.of());
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        int count = backfillService.backfillBatch(0L, 100);

        assertThat(count).isZero();
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class));
    }

    @Test
    @DisplayName("전체 backfill은 마지막 docId 이후 batch를 반복 처리한다")
    void backfillAll_success() {
        User user = user(10L);
        Doc first = doc(1L, user, "문서 1", LocalDateTime.of(2026, 1, 1, 10, 0));
        Doc second = doc(2L, user, "문서 2", LocalDateTime.of(2026, 1, 2, 10, 0));

        when(docRepository.findBackfillBatch(0L, PageRequest.of(0, 1))).thenReturn(List.of(first));
        when(docRepository.findBackfillBatch(1L, PageRequest.of(0, 1))).thenReturn(List.of(second));
        when(docRepository.findBackfillBatch(2L, PageRequest.of(0, 1))).thenReturn(List.of());
        when(branchRepository.findLatestSaveIdsByDocIds(List.of(1L))).thenReturn(List.of());
        when(branchRepository.findLatestSaveIdsByDocIds(List.of(2L))).thenReturn(List.of());
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, new BsonInt64(1L)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, new BsonInt64(2L)));

        int count = backfillService.backfillAll(1);

        assertThat(count).isEqualTo(2);
        verify(mongoTemplate, times(2)).upsert(any(Query.class), any(Update.class), eq(DocListReadModel.class));
    }

    @Test
    @DisplayName("batch size가 0 이하면 예외가 발생한다")
    void backfillBatch_fail_invalidSize() {
        assertThatThrownBy(() -> backfillService.backfillBatch(0L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("test@test.com")
                .name("tester")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Doc doc(Long docId, User user, String title, LocalDateTime time) {
        Doc doc = Doc.builder()
                .title(title)
                .user(user)
                .build();
        ReflectionTestUtils.setField(doc, "id", docId);
        ReflectionTestUtils.setField(doc, "createdAt", time);
        ReflectionTestUtils.setField(doc, "updatedAt", time.plusHours(1));
        return doc;
    }

    private void attachReadyThumbnail(Doc doc, String objectKey) {
        Image image = Image.builder()
                .userId(doc.getUser().getId())
                .docId(doc.getId())
                .originalFileName("thumbnail.webp")
                .objectKey(objectKey)
                .contentType("image/webp")
                .size(1024L)
                .purpose(Purpose.DOC_THUMBNAIL)
                .build();
        image.activate();

        Thumbnail thumbnail = Thumbnail.builder()
                .doc(doc)
                .build();
        thumbnail.complete(image, "signature");
        ReflectionTestUtils.setField(doc, "thumbnail", thumbnail);
    }

}
