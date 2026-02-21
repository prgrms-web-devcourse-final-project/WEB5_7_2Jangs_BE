package io.ejangs.docsa.domain.commit.app.merge;

import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.EdgeMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
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
public class MergeMySqlTxService {

    private final CommitQueryService commitQueryService;
    private final SaveService saveService;
    private final EdgeService edgeService;
    private final BranchService branchService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Commit createMySqlPart(Doc doc, Branch baseBranch, Branch targetBranch,
            MergeCommitRequest request, String commitMongoId) {
        Commit commit = CommitMapper.toEntity(targetBranch, request);
        commit.initializeCommitMongoId(commitMongoId);
        Commit savedCommit = commitQueryService.saveAndFlush(commit);

        targetBranch.addCommit(savedCommit);

        Commit baseCommit = getLeafCommit(baseBranch);
        Commit targetCommit = getLeafCommit(targetBranch);

        Edge edge1 = EdgeMapper.toEntity(doc, baseCommit, savedCommit);
        Edge edge2 = EdgeMapper.toEntity(doc, targetCommit, savedCommit);
        edgeService.saveEdge(edge1);
        edgeService.saveEdge(edge2);

        targetBranch.updateLeafCommit(savedCommit);
        String saveMongoId = saveService.deleteSaveIfExists(targetBranch);
        RenewUpdatedAtHelper.touch(targetBranch);
        branchService.saveBranch(targetBranch);

        MongoIdsDto saveCleanupIds = new MongoIdsDto(
                saveMongoId == null ? null : List.of(saveMongoId),
                null,
                null
        );
        eventPublisher.publishEvent(saveCleanupIds);

        return savedCommit;
    }

    private Commit getLeafCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }
}
