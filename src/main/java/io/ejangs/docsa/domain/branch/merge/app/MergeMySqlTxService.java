package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchWriter;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.merge.app.MergeService.MergeContext;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.domain.commit.app.CommitReader;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.save.app.SaveWriter;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationCompletionService;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;

@Service
@RequiredArgsConstructor
public class MergeMySqlTxService {

    private final SaveWriter saveWriter;
    private final BranchWriter branchWriter;
    private final DomainEventOutboxPublisher domainEventOutboxPublisher;
    private final MongoCreateOperationCompletionService operationCompletionService;
    private final DocReader docReader;
    private final CommitReader commitReader;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MergeResponse createMySqlPart(
            MergeContext context,
            MergeRequest request,
            String saveMongoId,
            String operationId
    ) {
        MongoCreateOperation operation = operationCompletionService.lockPending(operationId);
        Doc doc = docReader.getById(context.doc().getId());
        Commit baseCommit = commitReader.getById(context.baseCommit().getId());
        Commit targetCommit = commitReader.getById(context.targetCommit().getId());

        Branch newBranch = branchWriter.createBranch(doc, request.branchName(), baseCommit);
        newBranch.updateMergeTargetCommit(targetCommit);

        Save save = saveWriter.createSave(newBranch, saveMongoId);
        newBranch.setSave(save);
        branchWriter.save(newBranch);
        RenewUpdatedAtHelper.touch(save);

        domainEventOutboxPublisher.publish(DomainEventType.DOC_ACTIVITY_CHANGED, AggregateType.DOC,
                doc.getId(), DocPayloadFactory.activityChanged(doc.getId(), save));

        operationCompletionService.complete(operation, newBranch.getId(), save.getId());

        return new MergeResponse(newBranch.getId(), save.getId());
    }

}
