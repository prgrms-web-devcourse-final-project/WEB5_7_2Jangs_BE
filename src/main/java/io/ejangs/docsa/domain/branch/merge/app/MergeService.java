package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchReader;
import io.ejangs.docsa.domain.commit.app.CommitReader;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationStart;
import io.ejangs.docsa.global.saga.create.app.MongoCreatePlanFactory;
import io.ejangs.docsa.global.saga.create.app.MongoCreateRequestHasher;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MergeService {

    private final DocReader docReader;
    private final BranchReader branchReader;
    private final CommitReader commitReader;
    private final MergeOrchestrator mergeOrchestrator;
    private final MongoCreateOperationService mongoCreateOperationService;
    private final MongoCreatePlanFactory mongoCreatePlanFactory;
    private final MongoCreateRequestHasher mongoCreateRequestHasher;

    public MergeResponse merge(
            Long docId,
            MergeRequest mergeRequest,
            Long userId,
            String operationId
    ) {
        String requestHash = mongoCreateRequestHasher.hash(
                List.of(MongoCreateOperationType.MERGE, userId, docId, mergeRequest)
        );
        MongoCreateOperationStart existing = mongoCreateOperationService.findExisting(
                operationId, userId, MongoCreateOperationType.MERGE, requestHash
        ).orElse(null);
        if (existing != null) {
            return new MergeResponse(existing.resultEntityId(), existing.resultSaveId());
        }

        MergeContext mergeContext = validateMerge(docId, mergeRequest, userId);
        MongoIdsDto plan = mongoCreatePlanFactory.singleSaveContent();

        return mergeOrchestrator.merge(
                mergeContext, mergeRequest, userId, operationId, requestHash, plan
        );
    }

    private MergeContext validateMerge(Long docId, MergeRequest mergeRequest, Long userId) {
        Doc doc = docReader.getByIdAndUserId(docId, userId);
        branchReader.checkDuplicatedWithBranchName(docId, mergeRequest.branchName());

        Commit baseCommit = commitReader.getById(mergeRequest.baseCommitId());
        Commit targetCommit = commitReader.getById(mergeRequest.targetCommitId());

        commitReader.checkTwoCommitsInDocOwnedByUser(
                baseCommit.getId(),
                targetCommit.getId(),
                docId,
                userId
        );

        return new MergeContext(baseCommit, targetCommit, doc);
    }

    public record MergeContext(Commit baseCommit, Commit targetCommit, Doc doc) {

    }
}
