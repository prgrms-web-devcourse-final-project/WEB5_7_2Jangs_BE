package io.ejangs.docsa.domain.document.app;

import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    @Transactional
    public DocumentCreateResponse create(DocumentCreateRequest request, Long userId) {
        User mockUser = User.builder()
            .email("qoanstjdsla@gmail.com")
            .name("배문성")
            .password("password")
            .build();

        Document document = Document.builder()
            .title(request.title())
            .user(mockUser)
            .build();

        Document saved = documentRepository.save(document);
        return DocumentCreateResponse.of(saved);
    }
}
