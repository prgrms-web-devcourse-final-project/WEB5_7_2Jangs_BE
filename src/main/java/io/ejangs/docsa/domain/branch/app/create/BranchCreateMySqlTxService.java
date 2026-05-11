package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.app.BranchWriter;
import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.save.app.SaveQueryService;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchCreateMySqlTxService {

    private final BranchWriter branchWriter;
    private final SaveQueryService saveQueryService;
    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    @Transactional(rollbackFor = Exception.class)
    public BranchCreateResponse createMySqlPart(BranchCreateContext context, String saveContentId) {

        Branch newBranch = branchWriter.createBranch(context.doc(), context.branchName(), context.fromCommit());

        Save save = saveQueryService.createSave(newBranch, saveContentId);
        RenewUpdatedAtHelper.touch(save);

        domainEventOutboxPublisher.publish(DomainEventType.DOC_ACTIVITY_CHANGED, AggregateType.DOC, context.doc()
                .getId(), DocPayloadFactory.activityChanged(context.doc().getId(), save));

        return BranchMapper.toBranchCreateResponse(newBranch, save);
    }
}
