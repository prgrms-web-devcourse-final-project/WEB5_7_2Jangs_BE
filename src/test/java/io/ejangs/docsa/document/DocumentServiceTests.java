package io.ejangs.docsa.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTests {

    @InjectMocks
    private DocumentService documentService;

    @Mock
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("문서생성 성공 테스트")
    void documentCreatSuccessTest() throws Exception {
        // given
        DocumentCreateRequest request = new DocumentCreateRequest("테스트 문서");

        Document mockDoc = Document.builder()
            .title(request.title())
            .user(User.builder().name("배문성").build())
            .build();

        mockDoc.setId(1L);

        when(documentRepository.save(any())).thenReturn(mockDoc);

        // when
        DocumentCreateResponse response = documentService.create(request, 999L);

        // then
        assertEquals(1L, response.id());
        verify(documentRepository).save(any(Document.class));
    }
}
