package io.ejangs.docsa.domain.branch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BranchController.class)
@AutoConfigureMockMvc(addFilters = false)
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BranchService branchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("브랜치 생성 또는 저장 생성 API 성공")
    void createBranchOrSave_success() throws Exception {
        // given
        Long documentId = 1L;
        BranchCreateRequest request = new BranchCreateRequest("기능 브랜치", 100L);
        BranchCreateResponse response = new BranchCreateResponse(200L, 300L);

        Mockito.when(branchService.createBranchOrSave(eq(documentId), any()))
                .thenReturn(response);

        // when + then
        mockMvc.perform(post("/api/document/{documentId}/branch", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(200L))
                .andExpect(jsonPath("$.saveId").value(300L));
    }
}
