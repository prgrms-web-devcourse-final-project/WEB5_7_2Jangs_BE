package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.branch.app.BranchReader;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.mongo.util.MongoIdsCollector;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitService {

    private final DocReader docReader;
    private final BranchReader branchReader;
    private final CommitReader commitReader;
    private final CommitWriter commitWriter;
    private final EdgeService edgeService;

    private final CommitCreateOrchestrator commitCreateOrchestrator;
    private final CommitContentAssembler assembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    public CreateCommitResponse createCommit(Long docId,
            CreateCommitRequest request,
            Long userId) {

        branchReader.checkBranchInDocOwnedByUser(docId, request.branchId(), userId);

        Doc doc = docReader.getById(docId);
        Branch branch = branchReader.getById(request.branchId());

        String baseCommitCbsMongoId = commitReader.resolveBaseCommitCbsMongoId(branch);

        Commit newCommit = commitCreateOrchestrator.create(request, baseCommitCbsMongoId, doc,
                branch);

        return CommitMapper.toCreateCommitResponse(newCommit);
    }

    public CommitResponse getCommit(Long docId, Long commitId, Long userId) {
        docReader.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> content = getWholeContent(commitId);
        return CommitMapper.toCommitResponse(content);
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = commitReader.getById(commitId);
        return assembler.assemble(commit.getCommitMongoId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCommit(Long docId, Long commitId, Long userId) {
        Doc doc = docReader.getByIdAndUserId(docId, userId);

        Commit commit = commitReader.getById(commitId);
        // LeafCommit일 경우에만 삭제 가능
        checkLeafCommit(commit);
        // 어느 브랜치의 FromCommit이나 MergeTargetCommit일 경우 삭제 불가능
        checkFromOrMergeTargetCommit(commit);

        // 간선을 삭제하면서 새로 LeafCommit이 될 Commit들을 수집
        List<Commit> prevCommits = edgeService.cutEdge(doc, commitId);

        Branch currentBranch = commit.getBranch();

        Commit prevCommitInSameBranch = prevCommits.stream()
                .filter(prevCommit -> prevCommit.getBranch().getId().equals(currentBranch.getId()))
                .findFirst()
                .orElse(null);

        currentBranch.detachRootCommit(commit);

        if (prevCommitInSameBranch != null) {
            currentBranch.updateLeafCommit(prevCommitInSameBranch);
        }

        RenewUpdatedAtHelper.touch(currentBranch);

        MongoIdsDto commitDeleteMongoIds = mongoIdsCollector.collectFrom(prevCommits, commit);

        commitWriter.deleteById(commit.getId());

        mongoDeleteJobEnqueuer.enqueueCommitDeletion(commitId, commitDeleteMongoIds);
    }

    private void checkFromOrMergeTargetCommit(Commit commit) {
        if (branchReader.checkFromCommitOrMergeCommitInBranch(commit)) {
            throw new CustomException(CommitErrorCode.CAN_NOT_DELETE_COMMIT);
        }
    }

    public void checkLeafCommit(Commit commit) {
        if (!commit.getId().equals(commit.getBranch().getLeafCommit().getId())) {
            throw new CustomException(CommitErrorCode.IS_NOT_LEAF_COMMIT);
        }
    }
}
