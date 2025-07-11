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
    @DisplayName("POST /api/document/{id}/branch → 201 + body")
    void createBranch_returns201() throws Exception {
        long docId = 42L;

        BranchCreateRequest req = new BranchCreateRequest("new-branch", null);
        BranchCreateResponse resp = new BranchCreateResponse(99L, 123L);

        Mockito.when(branchService.createBranch(eq(docId), any(BranchCreateRequest.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/document/{documentId}/branch", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(99))
                .andExpect(jsonPath("$.tempId").value(123));
    }
}
