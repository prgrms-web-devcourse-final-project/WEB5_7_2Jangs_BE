package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
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

@Service
@RequiredArgsConstructor
public class CommitMySqlTxService {

    private final CommitQueryService commitQueryService;
    private final EdgeService edgeService;
    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    @Transactional(rollbackFor = Exception.class)
    public Commit createMySqlPart(Doc doc, Branch branch, CreateCommitRequest request, String commitCbsMongoId) {
        Commit newCommit = CommitMapper.toEntity(branch, request);
        newCommit.initializeCommitMongoId(commitCbsMongoId);
        newCommit = commitQueryService.saveAndFlush(newCommit);

        branch.updateRootCommit(newCommit);

        Commit baseCommit = Optional.ofNullable(branch.getLeafCommit()).orElse(branch.getFromCommit());
        branch.updateLeafCommit(newCommit);

        // 새로운 간선 생성
        if (baseCommit != null) {
            Edge newEdge = EdgeMapper.toEntity(doc, baseCommit, newCommit);
            edgeService.saveEdge(newEdge);
        }

        RenewUpdatedAtHelper.touch(branch.getSave());

        domainEventOutboxPublisher.publish(DomainEventType.DOC_ACTIVITY_CHANGED, AggregateType.DOC, doc.getId(),
                DocPayloadFactory.activityChanged(doc.getId(), branch.getSave()));

        return newCommit;
    }
}
