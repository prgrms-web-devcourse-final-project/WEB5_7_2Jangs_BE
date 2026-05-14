package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchWriter;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.merge.app.MergeService.MergeContext;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
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

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MergeMySqlTxService {

    private final SaveWriter saveWriter;
    private final BranchWriter branchWriter;
    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    public MergeResponse createMySqlPart(MergeContext context, MergeRequest request, String saveMongoId) {

        Branch newBranch = branchWriter.createBranch(context.doc(), request.branchName(), context.baseCommit());
        newBranch.updateMergeTargetCommit(context.targetCommit());

        Save save = saveWriter.createSave(newBranch, saveMongoId);
        newBranch.setSave(save);
        branchWriter.save(newBranch);
        RenewUpdatedAtHelper.touch(save);

        domainEventOutboxPublisher.publish(DomainEventType.DOC_ACTIVITY_CHANGED, AggregateType.DOC, context.doc()
                .getId(), DocPayloadFactory.activityChanged(context.doc().getId(), save));

        return new MergeResponse(newBranch.getId(), save.getId());
    }

}
