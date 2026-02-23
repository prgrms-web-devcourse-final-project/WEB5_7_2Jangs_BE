package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.entity.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocCreateMySqlTxService {

    private final DocQueryService docQueryService;
    private final BranchQueryService branchQueryService;
    private final SaveService saveService;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional(rollbackFor = Exception.class)
    public DocCreateResponse createMySqlPart(String title, User user,
            String saveContentId) {

        Doc doc = docQueryService.create(user, title);
        Branch defaultBranch = branchQueryService.createBranch(doc, defaultBranchName);
        Save defaultSave = saveService.createSave(defaultBranch, saveContentId);
        return DocMapper.toCreateResponse(doc, defaultSave);
    }



}
