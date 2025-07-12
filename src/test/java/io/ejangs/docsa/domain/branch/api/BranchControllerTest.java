package io.ejangs.docsa.domain.branch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.BranchRenameResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        mockMvc.perform(post("/api/document/{documentId}/branch", docId).contentType(
                        MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.branchId").value(99))
                .andExpect(jsonPath("$.saveId").value(123));
    }

    @Test
    @DisplayName("PATCH /api/document/{document_id}/branch/{branch_id} → 200")
    void renameBranch_Success() throws Exception {
        Long documentId = 5L;
        Long branchId = 8L;
        String newName = "updated-branch";

        BranchRenameResponse resp = new BranchRenameResponse(branchId, newName);

        Mockito.when(branchService.renameBranch(documentId, branchId, newName)).thenReturn(resp);

        mockMvc.perform(patch("/api/document/{documentId}/branch/{branchId}", documentId,
                        branchId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newName\": \"" + newName + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(branchId))
                .andExpect(jsonPath("$.name").value(newName));
    }

    @Test
    @DisplayName("PATCH /api/document/{document_id}/branch/{branch_id} → 404")
    void renameBranch_InvalidName() throws Exception {
        Long documentId = 5L;
        Long branchId = 8L;

        mockMvc.perform(patch("/api/document/{documentId}/branch/{branchId}", documentId,
                        branchId).contentType(MediaType.APPLICATION_JSON).content("{\"newName\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
