package io.ejangs.docsa.domain.document.app;

import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.dto.DocumentListSidebarResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.document.util.DocumentMapper;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.entity.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentCreateResponse create(DocumentCreateRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        Document document = Document.builder()
                .title(request.title())
                .user(user)
                .build();

        Document saved = documentRepository.save(document);

        user.addDocument(saved);

        return DocumentMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentListSidebarResponse> readSidebar(Long userId) {
        //추후 Principal에서 추출 예정
        User user = getUserOrThrow(userId);
        return documentRepository.findDocumentSidebarByUserId(user.getId());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

}
