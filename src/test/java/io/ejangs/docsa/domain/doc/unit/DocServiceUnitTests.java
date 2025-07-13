package io.ejangs.docsa.domain.doc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
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
public class DocServiceUnitTests {

    @InjectMocks
    private DocService docService;

    @Mock
    private DocRepository docRepository;

    @Mock
    private UserRepository userRepository;


    @Test
    @DisplayName("사이드바 문서 목록 조회 성공 테스트")
    void getSimpleDocumentListSuccess() throws Exception {

        //given
        Long userId = 1L;
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", 1L);

        List<DocListSimpleResponse> simpleDocuementList = List.of(
                new DocListSimpleResponse(1L, "문서1", LocalDateTime.now(),
                        LocalDateTime.now().plusHours(3)),
                new DocListSimpleResponse(2L, "문서2", LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusDays(3))
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(docRepository.getSimpleList(userId)).thenReturn(simpleDocuementList);

        //when
        List<DocListSimpleResponse> result = docService.getSimpleList(userId);

        //then
        assertEquals(2, result.size());
        assertEquals("문서1", result.getFirst().title());
        verify(userRepository).findById(userId);
        verify(docRepository).getSimpleList(userId);
    }

    @Test
    @DisplayName("문서 제목 수정 성공 테스트")
    void updateDocTitleSuccess() throws Exception {
        //given
        Long userId = 1L;

        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Long docId = 10L;
        String newTitle = "new title";

        Doc doc = Doc.builder()
                .title("old title")
                .user(user)
                .build();
        ReflectionTestUtils.setField(doc, "id", docId);
        ReflectionTestUtils.setField(doc, "updatedAt", LocalDateTime.now());

        DocTitleRequest request = new DocTitleRequest(newTitle);

        DocTitleUpdateResponse response = new DocTitleUpdateResponse(docId, newTitle,
                LocalDateTime.now());

        when(docRepository.existsByUserIdAndTitle(userId, newTitle)).thenReturn(false);
        when(docRepository.getDocByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        //when
        DocTitleUpdateResponse result = docService.updateTitle(userId, docId, request);

        //then
        verify(docRepository).existsByUserIdAndTitle(userId, newTitle);
        verify(docRepository).getDocByIdAndUserId(docId, userId);
        assertEquals(newTitle, doc.getTitle());
        assertEquals(response.id(), doc.getId());
        assertEquals(response.title(), result.title());
    }

}
