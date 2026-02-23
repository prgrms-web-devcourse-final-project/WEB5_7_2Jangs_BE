package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchCreateMySqlTxService {

    private final BranchQueryService branchQueryService;
    private final SaveService saveService;

    @Transactional
    public BranchCreateResponse createBranchOrSave(BranchCreateContext context, String saveContentId) {
        Branch targetBranch = context.fromBranch();

        if (context.createNewBranch()) {
            targetBranch = branchQueryService.createBranch(context.doc(), context.branchName(), context.fromCommit());
        }

        Save save = saveService.createSave(targetBranch, saveContentId);
        RenewUpdatedAtHelper.touch(save);

        return BranchMapper.toBranchCreateResponse(targetBranch, save);
    }
}
