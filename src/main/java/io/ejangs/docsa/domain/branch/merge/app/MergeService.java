package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.CompareMergeResponse;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MergeService {

    private final DocQueryService docQueryService;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final CommitContentAssembler commitContentAssembler;
    private final MergeOrchestrator mergeOrchestrator;

    @Transactional(readOnly = true)
    public CompareMergeResponse compare(Long docId, Long baseId, Long targetId, Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);

        List<Map<String, Object>> baseContent = getWholeContent(baseId);
        List<Map<String, Object>> targetContent = getWholeContent(targetId);

        return new CompareMergeResponse(baseContent, targetContent);
    }

    @Transactional(rollbackFor = Exception.class)
    public MergeResponse merge(Long docId, MergeRequest mergeRequest, Long userId) {
        MergeContext mergeContext = validateMerge(docId, mergeRequest, userId);

        return mergeOrchestrator.merge(
                mergeContext.doc(),
                mergeContext.baseCommit(),
                mergeRequest
        );
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = commitQueryService.getById(commitId);
        return commitContentAssembler.assemble(commit.getCommitMongoId());
    }

    private MergeContext validateMerge(Long docId, MergeRequest mergeRequest, Long userId) {
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);
        branchQueryService.checkDuplicatedWithBranchName(docId, mergeRequest.branchName());

        Commit baseCommit = commitQueryService.getById(mergeRequest.baseCommitId());
        Commit targetCommit = commitQueryService.getById(mergeRequest.targetCommitId());

        commitQueryService.checkTwoCommitsInDocOwnedByUser(
                baseCommit.getId(),
                targetCommit.getId(),
                docId,
                userId
        );

        return new MergeContext(baseCommit, doc);
    }

    private record MergeContext(Commit baseCommit, Doc doc) {

    }
}
