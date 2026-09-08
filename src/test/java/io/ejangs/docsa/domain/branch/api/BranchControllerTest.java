package io.ejangs.docsa.domain.branch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.request.BranchRenameRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.global.security.WithCustomMockUser;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BranchController.class)
@WithCustomMockUser
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
        Long mockUserId = 1L;
        BranchCreateRequest request = new BranchCreateRequest("기능 브랜치", 100L);
        BranchCreateResponse response = new BranchCreateResponse(200L, 300L);

        Mockito.when(branchService.createBranch(eq(documentId), any(), eq(mockUserId), anyString()))
                .thenReturn(response);

        // when + then
        mockMvc.perform(post("/api/document/{documentId}/branch", documentId)
                        .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(200L))
                .andExpect(jsonPath("$.saveId").value(300L));
    }
    @Test
    @DisplayName("브랜치 이름 수정 API 성공")
    void renameBranch_success() throws Exception {
        Long documentId = 1L;
        Long branchId = 2L;
        Long userId = 1L;
        String newName = "수정된 이름";

        BranchRenameRequest request = new BranchRenameRequest(newName);
        BranchRenameResponse response = new BranchRenameResponse(branchId, newName);

        Mockito.when(branchService.renameBranch(documentId, branchId, newName, userId))
                .thenReturn(response);

        mockMvc.perform(patch("/api/document/{documentId}/branch/{branchId}", documentId, branchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(branchId))
                .andExpect(jsonPath("$.name").value(newName));
    }
    @Test
    @DisplayName("브랜치 삭제 API 성공")
    void deleteBranch_success() throws Exception {
        Long documentId = 1L;
        Long branchId = 2L;
        Long userId = 1L;

        Mockito.doNothing().when(branchService).deleteBranch(documentId, branchId, userId);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/document/{documentId}/branch/{branchId}", documentId, branchId))
                .andExpect(status().isNoContent());

        Mockito.verify(branchService).deleteBranch(documentId, branchId, userId);
    }
}
