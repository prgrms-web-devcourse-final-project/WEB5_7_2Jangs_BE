package io.ejangs.docsa.domain.document.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.document.api.DocumentController;
import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.dto.DocumentListSimpleResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerUnitTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("문서 생성 성공 컨트롤러 테스트")
    void createDocumentApi() throws Exception {

        //given
        DocumentCreateRequest request = new DocumentCreateRequest("적당한 길이의 제목");
        Long documentId = 1L;

        //when, then
        when(documentService.create(any(DocumentCreateRequest.class), anyLong()))
                .thenReturn(new DocumentCreateResponse(documentId));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .param("userId", "1")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType("application/json")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(documentId))
                .andDo(print());
    }

    @Test
    @DisplayName("문서 리스트 조회 컨트롤러 테스트 - 사이드바")
    void getSimpleDocumentList() throws Exception {
        // given
        Long userId = 1L;

        List<DocumentListSimpleResponse> responseList = List.of(
                new DocumentListSimpleResponse(
                        1L,
                        "마이크로소프트",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1)
                ),
                new DocumentListSimpleResponse(
                        2L,
                        "구글",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(2)
                )
        );

        when(documentService.getSimpleDocumentList(anyLong())).thenReturn(responseList);

        //when, then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/sidebar")
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].title").value("마이크로소프트"))
                .andExpect(jsonPath("$.[1].title").value("구글"))
                .andDo(print());
    }

    @Test
    @DisplayName("문서 제목이 빈값이면 400반환")
    void throwExCusOfBlankTitle() throws Exception {
        DocumentCreateRequest request = new DocumentCreateRequest("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .param("userId", "1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("문서제목을 입력해주세요."))
                .andDo(print());
    }
}

