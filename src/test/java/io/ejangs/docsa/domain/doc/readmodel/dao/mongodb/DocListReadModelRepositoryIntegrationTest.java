package io.ejangs.docsa.domain.doc.readmodel.dao.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DocListReadModelRepositoryIntegrationTest {

    @Autowired
    private DocListReadModelRepository docListReadModelRepository;

    private final Long userId = System.currentTimeMillis();
    private final Long activeDocId = userId + 1;
    private final Long deletedDocId = userId + 2;

    @BeforeEach
    void setUp() {
        docListReadModelRepository.deleteAllById(List.of(activeDocId, deletedDocId));
    }

    @AfterEach
    void cleanUp() {
        docListReadModelRepository.deleteAllById(List.of(activeDocId, deletedDocId));
    }

    @Test
    @DisplayName("문서 목록 조회시 삭제된 read model은 제외한다")
    void findByUserIdAndDeletedFalse_excludesDeleted() {
        DocListReadModel active = readModel(activeDocId, "문서 1", 1L);
        DocListReadModel deleted = readModel(deletedDocId, "문서 2", 2L);
        deleted.markDeleted(3L);
        docListReadModelRepository.saveAll(List.of(active, deleted));

        Page<DocListReadModel> result = docListReadModelRepository.findByUserIdAndDeletedFalse(
                userId,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(DocListReadModel::getId)
                .containsExactly(activeDocId);
    }

    @Test
    @DisplayName("문서 목록 검색시 삭제된 read model은 제외한다")
    void searchByTitle_excludesDeleted() {
        DocListReadModel active = readModel(activeDocId, "검색 문서", 1L);
        DocListReadModel deleted = readModel(deletedDocId, "검색 문서", 2L);
        deleted.markDeleted(3L);
        docListReadModelRepository.saveAll(List.of(active, deleted));

        Page<DocListReadModel> result =
                docListReadModelRepository.findByUserIdAndDeletedFalseAndTitleContainingIgnoreCase(
                        userId,
                        "검색",
                        PageRequest.of(0, 10)
                );

        assertThat(result.getContent())
                .extracting(DocListReadModel::getId)
                .containsExactly(activeDocId);
    }

    private DocListReadModel readModel(Long docId, String title, Long eventId) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        return DocListReadModel.create(
                new DocCreatedPayload(
                        docId,
                        userId,
                        title,
                        createdAt,
                        updatedAt,
                        10L,
                        null,
                        ThumbnailStatus.EMPTY
                ),
                eventId
        );
    }
}
