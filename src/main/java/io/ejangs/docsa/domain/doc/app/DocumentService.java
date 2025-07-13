package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.doc.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocumentTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocumentMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentCreateResponse create(DocumentTitleRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        String title = request.title();
        checkTitleDuplicate(userId, title);

        Doc doc = Doc.builder()
                .title(title)
                .user(user)
                .build();

        Doc saved = documentRepository.save(doc);

        user.addDocument(saved);
        user.touch();

        return DocumentMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentListSimpleResponse> getSimpleList(Long userId) {
        //추후 Principal에서 추출 예정
        User user = getUserOrThrow(userId);
        return documentRepository.getSimpleList(user.getId());
    }

    @Transactional
    public DocumentTitleUpdateResponse updateTitle(Long userId, Long documentId,
            DocumentTitleRequest request) {
        String title = request.title();
        checkTitleDuplicate(userId, title);

        Doc doc = getDocByIdAndUserId(documentId, userId);
        doc.updateTitle(title);

        return DocumentMapper.toUpdateResponse(doc);
    }

    private void checkTitleDuplicate(Long userId, String title) {
        Boolean alreadyExistsTitle = documentRepository.existsByUserIdAndTitle(userId, title);
        if (alreadyExistsTitle) {
            throw new CustomException(DocumentErrorCode.TITLE_DUPLICATION);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    private Doc getDocByIdAndUserId(Long documentId, Long userId) {
        return documentRepository.getDocByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }

}
