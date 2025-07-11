package io.ejangs.docsa.domain.commit.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.commit.app.CommitService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommitController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommitControllerMockTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommitService commitService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("커밋 저장 성공 테스트")
    void saveCommit_success() throws Exception {
        // given
        Long documentId = 1L;
        Long userId = 1L;

        CreateCommitRequest request = new CreateCommitRequest(
                "문서 초안 작성 완료",
                "1차 초안 커밋입니다.",
                1L,
                List.of(
                        new BlockDto("mhTl6ghSkV", "paragraph",
                                Map.of("text", "Hey. Meet the new Editor."), null),
                        new BlockDto("os_YI4eub4", "list", Map.of(
                                "type", "unordered",
                                "items", List.of(
                                        "It is a block-style editor",
                                        "It returns clean data output in JSON"
                                )
                        ), null)
                ),
                List.of("mhTl6ghSkV", "os_YI4eub4")
        );

        CreateCommitResponse mockResponse = new CreateCommitResponse(100L); // 예시 응답

        when(commitService.createCommit(documentId, userId, request)).thenReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/document/{document_Id}/commit/{user_Id}", documentId, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L));
    }
}
