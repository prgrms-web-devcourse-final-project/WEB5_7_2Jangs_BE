package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.doc.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.doc.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocumentMapper;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
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
    public DocumentCreateResponse create(DocumentCreateRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        Doc doc = Doc.builder()
                .title(request.title())
                .user(user)
                .build();

        Doc saved = documentRepository.save(doc);

        user.addDocument(saved);

        return DocumentMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentListSimpleResponse> getSimpleDocumentList(Long userId) {
        //추후 Principal에서 추출 예정
        User user = getUserOrThrow(userId);
        return documentRepository.getSimpleDocumentList(user.getId());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

}
