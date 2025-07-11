package io.ejangs.docsa.domain.document.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.document.util.DocumentTestUtils;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.entity.dao.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceUnitTests {

    @InjectMocks
    private DocumentService documentService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("사이드바 문서 목록 조회 성공 테스트")
    void getSimpleDocumentListSuccess() throws Exception {

        //given
        Long userId = 1L;
        User user = DocumentTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", 1L);

        List<DocumentListSimpleResponse> simpleDocuementList = List.of(
                new DocumentListSimpleResponse(1L, "문서1", LocalDateTime.now(),
                        LocalDateTime.now().plusHours(3)),
                new DocumentListSimpleResponse(2L, "문서2", LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusDays(3))
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(documentRepository.getSimpleDocumentList(userId)).thenReturn(simpleDocuementList);

        //when
        List<DocumentListSimpleResponse> result = documentService.getSimpleDocumentList(userId);

        //then
        assertEquals(2, result.size());
        assertEquals("문서1", result.getFirst().title());
        verify(userRepository).findById(userId);
        verify(documentRepository).getSimpleDocumentList(userId);
    }

}
