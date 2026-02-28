package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteRetryService;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitCreateOrchestrator {

    private final CommitMySqlTxService commitMySqlTxService;
    private final CommitMongoTxService commitMongoTxService;
    private final MongoDeleteRetryService mongoDeleteRetryService;

    public Commit create(CreateCommitRequest request, String baseCommitCbsMongoId, Doc doc, Branch branch) {
        MongoIdsDto compensateTarget = commitMongoTxService.createMongoPart(request, baseCommitCbsMongoId);

        // DTO 개선 검토
        String createdCbsId = compensateTarget.commitBlockSequenceIds().getFirst();

        try {
            return commitMySqlTxService.createMySqlPart(doc, branch,
                    request, createdCbsId);
        } catch (Exception e) {
            log.warn("[SAGA] 커밋 생성 실패 -> Mongo 보상 삭제. ", e);
            mongoDeleteRetryService.deleteMongoData(compensateTarget);
            throw e;
        }
    }
}
