package io.ejangs.docsa.domain.document.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.document.util.DocumentTestUtils;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class DocumentRepositoryIntegrationTests {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentService documentService;

    @Test
    @DisplayName("사이드바 문서리스트 조회")
    void getSimpleDocumentList() throws Exception {
        //given
        User user = DocumentTestUtils.createUser();
        userRepository.save(user);

        List<Document> documentList = DocumentTestUtils.createDocumentList(5, user);
        documentRepository.saveAll(documentList);

        //when
        List<DocumentListSimpleResponse> results = documentService.getSimpleDocumentList(
                user.getId());

        //then
        assertEquals(5, results.size());
        assertEquals("테스트 문서 5", results.getFirst().title());
    }
}
