package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MergeService {

    private final DocReader docReader;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final CommitContentAssembler commitContentAssembler;
    private final MergeOrchestrator mergeOrchestrator;

    @Transactional(rollbackFor = Exception.class)
    public MergeResponse merge(Long docId, MergeRequest mergeRequest, Long userId) {
        MergeContext mergeContext = validateMerge(docId, mergeRequest, userId);

        return mergeOrchestrator.merge(
                mergeContext, mergeRequest
        );
    }

    private MergeContext validateMerge(Long docId, MergeRequest mergeRequest, Long userId) {
        Doc doc = docReader.getByIdAndUserId(docId, userId);
        branchQueryService.checkDuplicatedWithBranchName(docId, mergeRequest.branchName());

        Commit baseCommit = commitQueryService.getById(mergeRequest.baseCommitId());
        Commit targetCommit = commitQueryService.getById(mergeRequest.targetCommitId());

        commitQueryService.checkTwoCommitsInDocOwnedByUser(
                baseCommit.getId(),
                targetCommit.getId(),
                docId,
                userId
        );

        return new MergeContext(baseCommit, targetCommit, doc);
    }

    public record MergeContext(Commit baseCommit, Commit targetCommit, Doc doc) {

    }
}
