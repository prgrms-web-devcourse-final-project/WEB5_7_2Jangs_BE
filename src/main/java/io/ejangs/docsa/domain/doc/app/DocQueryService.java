package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocQueryService {

    private final UserRepository userRepository;
    private final DocRepository docRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;

    private final DocListAssembler docListAssembler;

    // 추후 User 도메인으로 이동 필요
    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    }

    // branch로 이동해도 좋을듯
    public Branch createDefaultBranch(Doc doc, String defaultBranchName) {
        Branch branch =
                branchRepository.save(Branch.builder().name(defaultBranchName).doc(doc).build());
        doc.addBranch(branch);
        RenewUpdatedAtHelper.touch(branch);
        return branch;
    }

    // save로 이동
    public Save createDefaultSave(Branch branch, String mongoID) {
        return saveRepository.save(Save.builder().branch(branch).saveMongoId(mongoID).build());
    }


    public Doc create(User user, String title) {
        Doc doc = docRepository.save(Doc.builder().title(title).user(user).build());
        docRepository.flush();
        user.addDocument(doc);
        return doc;
    }

    public void checkTitleDuplicate(Long userId, String title) {
        Boolean alreadyExistsTitle = docRepository.existsByUserIdAndTitle(userId, title);
        if (alreadyExistsTitle) {
            throw new CustomException(DocErrorCode.TITLE_DUPLICATION);
        }
    }

    @Transactional(readOnly = true)
    public Doc getById(Long id) {
        return docRepository.findById(id)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Doc getByIdAndUserId(Long docId, Long userId) {
        return docRepository.getDocByIdAndUserId(docId, userId)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

    public void checkByIdAndUserId(Long docId, Long userId) {
        if (!docRepository.existsByIdAndUserId(docId, userId)) {
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Page<Doc> getPageByUserId(Long userId, Pageable pageable) {
        return docRepository.findAllByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Doc> searchByTitle(String keyword, Long userId, Pageable pageable) {
        return docRepository.searchDocByTitle(keyword, userId, pageable);
    }

}
