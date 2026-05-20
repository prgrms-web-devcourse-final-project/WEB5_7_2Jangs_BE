package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocReader {

    private static final String DOC_TITLE_UNIQUE_CONSTRAINT = "uk_user_title";

    private final UserRepository userRepository;
    private final DocRepository docRepository;

    // UserQueryService 분리 이후 이동 예정 (현재는 구조 변경 범위 최소화를 위해 보류)
    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    }

    public Doc create(User user, String title) {
        try {
            Doc doc = docRepository.save(Doc.builder().title(title).user(user).build());
            docRepository.flush();
            user.addDocument(doc);
            return doc;
        } catch (DataIntegrityViolationException e) {
            if (isTitleDuplicate(e)) {
                throw new CustomException(DocErrorCode.TITLE_DUPLICATION);
            }
            throw e;
        }
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

    private boolean isTitleDuplicate(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException constraintViolationException)) {
            return false;
        }

        String constraintName = constraintViolationException.getConstraintName();
        return constraintName != null && constraintName.endsWith(DOC_TITLE_UNIQUE_CONSTRAINT);
    }

}
