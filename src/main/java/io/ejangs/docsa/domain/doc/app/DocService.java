package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocService {

    private final DocRepository docRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocCreateResponse create(DocTitleRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        String title = request.title();
        checkTitleDuplicate(userId, title);

        Doc doc = Doc.builder()
                .title(title)
                .user(user)
                .build();

        Doc saved = docRepository.save(doc);
        docRepository.flush();

        user.addDocument(saved);
        return DocMapper.toCreateResponse(saved);
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

        Doc doc = getDocByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        checkTitleDuplicate(userId, title);
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
