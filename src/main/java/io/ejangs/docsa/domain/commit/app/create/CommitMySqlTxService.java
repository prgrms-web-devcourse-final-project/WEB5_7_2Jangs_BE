package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.edge.util.EdgeMapper;
import io.ejangs.docsa.domain.save.app.SaveQueryService;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.outbox.app.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitMySqlTxService {

    private final CommitQueryService commitQueryService;
    private final SaveQueryService saveQueryService;
    private final EdgeService edgeService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @Transactional(rollbackFor = Exception.class)
    public Commit createMySqlPart(Doc doc, Branch branch, CreateCommitRequest request, String commitCbsMongoId) {
        Commit newCommit = CommitMapper.toEntity(branch, request);
        newCommit.initializeCommitMongoId(commitCbsMongoId);
        newCommit = commitQueryService.saveAndFlush(newCommit);

        branch.initializeRootCommitIfNull(newCommit);

        // 브랜치의 Save 삭제
        String saveMongoId = saveQueryService.deleteSaveIfExists(branch);

        Commit baseCommit = Optional.ofNullable(branch.getLeafCommit()).orElse(branch.getFromCommit());
        branch.updateLeafCommit(newCommit);

        // 새로운 간선 생성
        if (baseCommit != null) {
            Edge newEdge = EdgeMapper.toEntity(doc, baseCommit, newCommit);
            edgeService.saveEdge(newEdge);
        }

        RenewUpdatedAtHelper.touch(branch);

        MongoIdsDto saveCleanupIds = new MongoIdsDto(
                saveMongoId == null ? null : List.of(saveMongoId),
                null,
                null
        );

        mongoDeleteOutboxFactory.create(
                TriggerType.DELETE_AFTER_SAVE_SUCCESS,
                DomainType.SAVE,
                OriginType.COMMIT_ID,
                newCommit.getId(),
                saveCleanupIds
        );

        return newCommit;
    }
}
