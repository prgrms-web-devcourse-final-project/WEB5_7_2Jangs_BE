package io.ejangs.docsa.domain.doc.readmodel.app;

import com.mongodb.client.result.UpdateResult;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.LatestSaveIdDto;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.image.entity.Image;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocListReadModelBackfillService {

    private final DocRepository docRepository;
    private final BranchRepository branchRepository;
    private final MongoTemplate mongoTemplate;

    public int backfillAll(int batchSize) {
        validateBatchSize(batchSize);

        long lastDocId = 0L;
        int totalInserted = 0;

        while (true) {
            List<Doc> docs = findBatch(lastDocId, batchSize);
            if (docs.isEmpty()) {
                log.info("[DocListReadModelBackfill] done. totalInserted={}", totalInserted);
                return totalInserted;
            }

            int inserted = backfillDocs(docs);
            totalInserted += inserted;
            lastDocId = docs.getLast().getId();

            log.info(
                    "[DocListReadModelBackfill] batch processed. lastDocId={}, scanned={}, inserted={}, totalInserted={}",
                    lastDocId,
                    docs.size(),
                    inserted,
                    totalInserted
            );
        }
    }

    public int backfillBatch(Long lastDocId, int size) {
        validateBatchSize(size);

        List<Doc> docs = findBatch(lastDocId, size);
        int inserted = backfillDocs(docs);

        log.info(
                "[DocListReadModelBackfill] single batch processed. lastDocId={}, scanned={}, inserted={}",
                lastDocId,
                docs.size(),
                inserted
        );

        return inserted;
    }

    private List<Doc> findBatch(Long lastDocId, int size) {
        return docRepository.findBackfillBatch(lastDocId, PageRequest.of(0, size));
    }

    private int backfillDocs(List<Doc> docs) {
        if (docs.isEmpty()) {
            return 0;
        }

        List<Long> docIds = docs.stream()
                .map(Doc::getId)
                .toList();

        Map<Long, Long> recentSaveIds = branchRepository.findLatestSaveIdsByDocIds(docIds).stream()
                .collect(Collectors.toMap(
                        LatestSaveIdDto::docId,
                        LatestSaveIdDto::saveId,
                        (left, right) -> right
                ));

        return docs.stream()
                .map(doc -> toReadModel(doc, recentSaveIds.get(doc.getId())))
                .map(this::insertIfAbsent)
                .mapToInt(inserted -> inserted ? 1 : 0)
                .sum();
    }

    private boolean insertIfAbsent(DocListReadModel model) {
        Query query = Query.query(Criteria.where("_id").is(model.getId()));
        Update update = new Update()
                .setOnInsert("_id", model.getId())
                .setOnInsert("userId", model.getUserId())
                .setOnInsert("title", model.getTitle())
                .setOnInsert("createdAt", model.getCreatedAt())
                .setOnInsert("updatedAt", model.getUpdatedAt())
                .setOnInsert("recentSaveId", model.getRecentSaveId())
                .setOnInsert("thumbnailObjectKey", model.getThumbnailObjectKey())
                .setOnInsert("thumbnailStatus", model.getThumbnailStatus())
                .setOnInsert("deleted", model.isDeleted());

        UpdateResult result = mongoTemplate.upsert(query, update, DocListReadModel.class);
        return result.getUpsertedId() != null;
    }

    private void validateBatchSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("batch size must be positive");
        }
    }

    private DocListReadModel toReadModel(
            Doc doc,
            Long recentSaveId
    ) {
        Thumbnail thumbnail = doc.getThumbnail();
        Image currentImage = thumbnail != null ? thumbnail.getCurrentImage() : null;
        String thumbnailObjectKey = currentImage != null ? currentImage.getObjectKey() : null;
        ThumbnailStatus thumbnailStatus = thumbnail != null ? thumbnail.getStatus() : ThumbnailStatus.EMPTY;

        return DocListReadModel.backfill(
                doc.getId(),
                doc.getUser().getId(),
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                recentSaveId,
                thumbnailObjectKey,
                thumbnailStatus
        );
    }
}
