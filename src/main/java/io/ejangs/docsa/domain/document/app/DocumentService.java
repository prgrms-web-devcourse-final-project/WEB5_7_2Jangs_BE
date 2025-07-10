package io.ejangs.docsa.domain.document.app;

import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.document.util.DocumentMapper;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.entity.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DocumentCreateResponse create(DocumentCreateRequest request, Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Document document = Document.builder()
            .title(request.title())
            .user(user)
            .build();

        Document saved = documentRepository.save(document);
        return DocumentMapper.toCreateResponse(saved);
    }
}
