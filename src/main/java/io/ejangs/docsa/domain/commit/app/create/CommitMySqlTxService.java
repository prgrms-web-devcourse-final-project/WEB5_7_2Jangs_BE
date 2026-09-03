package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.branch.app.BranchReader;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitWriter;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.edge.util.EdgeMapper;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationCompletionService;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;

@Service
@RequiredArgsConstructor
public class CommitMySqlTxService {

    private final CommitWriter commitWriter;
    private final EdgeService edgeService;
    private final DomainEventOutboxPublisher domainEventOutboxPublisher;
    private final MongoCreateOperationCompletionService operationCompletionService;
    private final DocReader docReader;
    private final BranchReader branchReader;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Commit createMySqlPart(
            Doc doc,
            Branch branch,
            CreateCommitRequest request,
            String commitCbsMongoId,
            String operationId
    ) {
        MongoCreateOperation operation = operationCompletionService.lockPending(operationId);
        Doc managedDoc = docReader.getById(doc.getId());
        Branch managedBranch = branchReader.getById(branch.getId());

        Commit newCommit = CommitMapper.toEntity(managedBranch, request);
        newCommit.initializeCommitMongoId(commitCbsMongoId);
        newCommit = commitWriter.saveAndFlush(newCommit);

        managedBranch.updateRootCommit(newCommit);

        Commit baseCommit = Optional.ofNullable(managedBranch.getLeafCommit())
                .orElse(managedBranch.getFromCommit());
        managedBranch.updateLeafCommit(newCommit);

        // 새로운 간선 생성
        if (baseCommit != null) {
            Edge newEdge = EdgeMapper.toEntity(managedDoc, baseCommit, newCommit);
            edgeService.saveEdge(newEdge);
        }

        RenewUpdatedAtHelper.touch(managedBranch.getSave());

        domainEventOutboxPublisher.publish(DomainEventType.DOC_ACTIVITY_CHANGED, AggregateType.DOC,
                managedDoc.getId(),
                DocPayloadFactory.activityChanged(managedDoc.getId(), managedBranch.getSave()));

        operationCompletionService.complete(operation, newCommit.getId(), null);

        return newCommit;
    }
}
