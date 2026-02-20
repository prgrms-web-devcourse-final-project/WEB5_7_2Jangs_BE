package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.EdgeMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitMySqlTxService {

    private final CommitRepository commitRepository;
    private final SaveService saveService;
    private final EdgeService edgeService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Commit createMySqlPart(Doc doc, Branch branch, CreateCommitRequest request, String commitCbsMongoId) {
        Commit newCommit = saveCommit(branch, request);
        newCommit.initializeCommitMongoId(commitCbsMongoId);
        commitRepository.flush();

        branch.initializeRootCommitIfNull(newCommit);

        // 브랜치의 Save 삭제
        String saveMongoId = saveService.deleteSaveIfExists(branch);

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
        eventPublisher.publishEvent(saveCleanupIds);

        return newCommit;
    }

    private Commit saveCommit(Branch branch, CreateCommitRequest commitRequest) {
        Commit commit = CommitMapper.toEntity(branch, commitRequest);
        Commit savedCommit = commitRepository.save(commit);
        commitRepository.flush();
        branch.addCommit(savedCommit);
        return savedCommit;
    }

}
