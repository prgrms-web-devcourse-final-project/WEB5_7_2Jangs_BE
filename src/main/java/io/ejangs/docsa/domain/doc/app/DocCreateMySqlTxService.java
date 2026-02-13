package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocCreateMySqlTxService {

    private final DocQueryService docQueryService;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional(rollbackFor = Exception.class)
    public DocCreateResponse createMySqlPart(String title, User user,
            String saveContentId) {

        Doc doc = docQueryService.createDoc(user, title);
        Branch defaultBranch = docQueryService.createDefaultBranch(doc, defaultBranchName);
        Save defaultSave = docQueryService.createDefaultSave(defaultBranch, saveContentId);
        return DocMapper.toCreateResponse(doc, defaultSave);
    }



}
