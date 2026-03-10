package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.create.CommitCreateOrchestrator;
import io.ejangs.docsa.domain.commit.app.merge.MergeOrchestrator;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
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

    private final DocQueryService docQueryService;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final EdgeService edgeService;

    private final CommitCreateOrchestrator commitCreateOrchestrator;
    private final MergeOrchestrator mergeCommitOrchestrator;

    private final CommitContentAssembler assembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public CreateCommitResponse createCommit(Long docId,
            CreateCommitRequest request,
            Long userId) {

        branchQueryService.checkBranchInDocOwnedByUser(docId, request.branchId(), userId);

        Doc doc = docQueryService.getById(docId);
        Branch branch = branchQueryService.getById(request.branchId());

        String baseCommitCbsMongoId = commitQueryService.resolveBaseCommitCbsMongoId(branch);

        Commit newCommit = commitCreateOrchestrator.create(request, baseCommitCbsMongoId, doc,
                branch);

        return CommitMapper.toCreateCommitResponse(newCommit);
    }

    public CommitResponse getCommit(Long docId, Long commitId, Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> content = getWholeContent(commitId);
        return CommitMapper.toCommitResponse(content);
    }

    @Transactional(readOnly = true)
    public CompareMergeCommitResponse getCommitsForMerge(Long docId, Long baseId, Long targetId,
            Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> baseContent = getWholeContent(baseId);
        List<Map<String, Object>> targetContent = getWholeContent(targetId);
        return CommitMapper.toCompareMergeCommitResponse(baseContent, targetContent);
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = commitQueryService.getById(commitId);
        return assembler.assemble(commit.getCommitMongoId());
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateCommitResponse mergeCommit(Long docId, MergeCommitRequest mergeRequest,
            Long userId) {
        MergeBranches mergeBranches = prepareMergeBranches(docId, mergeRequest, userId);

        Doc doc = docQueryService.getById(docId);

        Commit mergedCommit = mergeCommitOrchestrator.merge(
                doc,
                mergeBranches.baseBranch(),
                mergeBranches.targetBranch(),
                mergeRequest
        );
        return CommitMapper.toCreateCommitResponse(mergedCommit);
    }

    private record MergeBranches(Branch baseBranch, Branch targetBranch) {

    }

    private MergeBranches prepareMergeBranches(Long docId, MergeCommitRequest mergeRequest,
            Long userId) {
        Branch baseBranch = getLeafCommitById(mergeRequest.baseCommitId()).getBranch();
        Branch targetBranch = getLeafCommitById(mergeRequest.targetCommitId()).getBranch();

        validateDifferentBranch(baseBranch, targetBranch);
        validateMergePermission(docId, userId, baseBranch, targetBranch);

        return new MergeBranches(baseBranch, targetBranch);
    }

    private Commit getLeafCommitById(Long commitId) {
        Commit commit = commitQueryService.getById(commitId);
        checkLeafCommit(commit);
        return commit;
    }

    private void validateDifferentBranch(Branch baseBranch, Branch targetBranch) {
        if (baseBranch.getId().equals(targetBranch.getId())) {
            throw new CustomException(CommitErrorCode.COMMIT_BAD_REQUEST);
        }
    }

    private void validateMergePermission(Long docId, Long userId, Branch baseBranch,
            Branch targetBranch) {
        branchQueryService.checkBranchInDocOwnedByUser(docId, baseBranch.getId(), userId);
        branchQueryService.checkBranchInDocOwnedByUser(docId, targetBranch.getId(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCommit(Long docId, Long commitId, Long userId) {
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);

        Commit commit = commitQueryService.getById(commitId);
        // LeafCommit일 경우에만 삭제 가능
        checkLeafCommit(commit);
        // 어느 브랜치의 FromCommit이나 RootCommit일 경우 삭제 불가능
        checkFromOrRootCommit(commit);

        // 간선을 삭제하면서 새로 LeafCommit이 될 Commit들을 수집
        List<Commit> prevCommits = edgeService.cutEdge(doc, commitId);

        for (Commit prevCommit : prevCommits) {
            Branch branch = prevCommit.getBranch();
            branch.updateLeafCommit(prevCommit);
            branch.removeCommit(commit);
            RenewUpdatedAtHelper.touch(branch);
        }

        MongoIdsDto commitDeleteMongoIds = mongoIdsCollector.collectFrom(prevCommits, commit);

        commitQueryService.deleteById(commit.getId());

        mongoDeleteOutboxFactory.create(
                TriggerType.DELETE,
                DomainType.COMMIT,
                OriginType.COMMIT_ID,
                commitId,
                commitDeleteMongoIds
        );
    }

    private void checkFromOrRootCommit(Commit commit) {
        if (branchQueryService.checkFromOrRootCommitInBranch(commit)) {
            throw new CustomException(CommitErrorCode.CAN_NOT_DELETE_COMMIT);
        }
    }

    public void checkLeafCommit(Commit commit) {
        if (!commit.getId().equals(commit.getBranch().getLeafCommit().getId())) {
            throw new CustomException(CommitErrorCode.IS_NOT_LEAF_COMMIT);
        }
    }
}
