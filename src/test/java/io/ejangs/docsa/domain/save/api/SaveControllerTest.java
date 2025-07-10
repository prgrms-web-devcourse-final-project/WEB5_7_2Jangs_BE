package io.ejangs.docsa.domain.save.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@WebMvcTest(controllers = SaveController.class)
@AutoConfigureMockMvc(addFilters = false)
class SaveControllerTest {

    @MockitoBean
    private SaveService saveService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("저장 데이터 덮어쓰기 성공")
    void updateSave_success() throws Exception {
        Long userId = 1L;
        Long documentId = 1L;
        Long saveId = 1L;
        String content = "my content";

        SaveUpdateIdDto dto = SaveUpdateIdDto.of(documentId, saveId, userId);
        SaveUpdateRequest request = new SaveUpdateRequest(content);

        when(saveService.updateSave(dto, request)).thenReturn(LocalDateTime.now());

        mockMvc.perform(
                        put("/api/document/{documentId}/save/{saveId}", documentId, saveId)
                                .param("userId", String.valueOf(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andDo(print());
    }

    @ParameterizedTest
    @DisplayName("path variable 이 비정상일 경우 예외 발생")
    @CsvSource({
            "abc,1",      // invalid documentId
            "1,xyz",      // invalid saveId
            "abc,xyz",    // both invalid
    })
    void updateSave_fail_invalidPathVariables(String documentId, String saveId) throws Exception {
        Long userId = 1L;
        String content = "my content";
        SaveUpdateRequest request = new SaveUpdateRequest(content);

        mockMvc.perform(
                        put("/api/document/{documentId}/save/{saveId}", documentId, saveId)
                                .param("userId", String.valueOf(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(MethodArgumentTypeMismatchException.class,
                        result.getResolvedException()))
                .andDo(print());
    }
}