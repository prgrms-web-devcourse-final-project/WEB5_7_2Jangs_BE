package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.domain.save.app.SaveQueryService;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class MergeMySqlTxService {

    private final SaveQueryService saveQueryService;
    private final BranchQueryService branchQueryService;

    public MergeResponse createMySqlPart(Doc doc, Commit baseCommit,
            MergeRequest request, String saveMongoId) {

        Branch newBranch = branchQueryService.createBranch(doc, request.branchName(), baseCommit);

        Save save = saveQueryService.createSave(newBranch, saveMongoId);
        newBranch.setSave(save);
        RenewUpdatedAtHelper.touch(newBranch);
        branchQueryService.save(newBranch);

        return new MergeResponse(newBranch.getId(), save.getId());
    }

}
