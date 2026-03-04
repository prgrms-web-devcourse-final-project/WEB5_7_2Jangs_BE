package io.ejangs.docsa.domain.commit.app.merge;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.global.mongo.deletion.dto.CommitMongoIdsDto;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OperationSource;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OperationType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutboxFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MergeOrchestrator {

    private final MergeMongoTxService mergeMongoTxService;
    private final MergeMySqlTxService mergeMySqlTxService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public Commit merge(Doc doc, Branch baseBranch, Branch targetBranch, MergeCommitRequest request) {
        CommitMongoIdsDto compensateTarget = mergeMongoTxService.createMongoPart(request.content());
        try {
            return mergeMySqlTxService.createMySqlPart(
                    doc,
                    baseBranch,
                    targetBranch,
                    request,
                    compensateTarget.cbsId()
            );
        } catch (Exception e) {
            log.warn("[SAGA] 머지 커밋 생성 실패 -> Mongo 삭제 Outbox 기록.", e);
            MongoIdsDto compensateMongoIds = new MongoIdsDto(
                    null,
                    List.of(compensateTarget.cbsId()),
                    compensateTarget.blockIds()
            );
            mongoDeleteOutboxFactory.create(OperationType.DELETE_COMMIT, OperationSource.COMPENSATION,
                    null, compensateTarget.cbsId(), compensateMongoIds);
            throw e;
        }
    }
}
