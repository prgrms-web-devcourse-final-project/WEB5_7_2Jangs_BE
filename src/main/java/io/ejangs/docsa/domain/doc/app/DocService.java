package io.ejangs.docsa.domain.doc.app;

import com.mongodb.MongoTimeoutException;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocRepository docRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional
    public DocCreateResponse create(DocTitleRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        String title = request.title();
        checkTitleDuplicate(userId, title);

        //Mongo 저장을 맨처음에 진행하여 실패시 예외 발생으로 인한 종료 -> 유사 트랜잭션
        SaveContent defaultSaveContent;
        try {
            defaultSaveContent = createDefaultSaveContent();
        } catch (MongoTimeoutException | DataAccessResourceFailureException e) {
            log.error("DefaultSaveContent: Mongo 저장 실패 원인 - {}", e.getMessage());
            throw new CustomException(DocErrorCode.FAIL_CREATE_DOCUMENT);
        }

        Doc doc = createDoc(user, title);

        Branch defaultBranch = createDefaultBranch(doc);

        createDefaultSave(defaultBranch, defaultSaveContent);

        return DocMapper.toCreateResponse(doc);
    }

    private Doc createDoc(User user, String title) {
        Doc doc = docRepository.save(Doc.builder()
                .title(title)
                .user(user)
                .build());
        user.addDocument(doc);
        user.touch();
        return doc;
    }

    private Branch createDefaultBranch(Doc doc) {
        Branch branch = branchRepository.save(Branch.builder()
                .name(defaultBranchName)
                .doc(doc)
                .build());
        doc.addBranch(branch);
        return branch;
    }

    private SaveContent createDefaultSaveContent() {
        return saveContentRepository.save(
                SaveContent.builder()
                        .content(new HashMap<>())
                        .build()
        );
    }

    private void createDefaultSave(Branch branch, SaveContent saveContent) {
        saveRepository.save(Save.builder()
                .branch(branch)
                .saveMongoId(saveContent.getId())
                .build());
    }

    @Transactional(readOnly = true)
    public List<DocListSimpleResponse> getSimpleList(Long userId) {
        //추후 Principal에서 추출 예정
        User user = getUserOrThrow(userId);
        return docRepository.getSimpleList(user.getId());
    }

    @Transactional
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId,
            DocTitleRequest request) {
        String title = request.title();
        checkTitleDuplicate(userId, title);

        Doc doc = getDocByIdAndUserId(docId, userId);
        doc.updateTitle(title);

        return DocMapper.toUpdateResponse(doc);
    }

    private void checkTitleDuplicate(Long userId, String title) {
        Boolean alreadyExistsTitle = docRepository.existsByUserIdAndTitle(userId, title);
        if (alreadyExistsTitle) {
            throw new CustomException(DocErrorCode.TITLE_DUPLICATION);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    private Doc getDocByIdAndUserId(Long documentId, Long userId) {
        return docRepository.getDocByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

}
