package io.ejangs.docsa.domain.save.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.global.security.WithCustomMockUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
@WithCustomMockUser
@AutoConfigureMockMvc(addFilters = false)
class SaveControllerTest {

    @MockitoBean
    private SaveService saveService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private SaveIdentifierDto dto;
    private List<Map<String, Object>> data;
    private SaveUpdateRequest request;

    @BeforeEach
    void setUp() {
        dto = SaveIdentifierDto.of(1L, 1L, 1L);
        data = List.of(
                Map.of("text1", "Key features"),
                Map.of("text2", "Key features")
        );
        request = new SaveUpdateRequest(data);
    }

    @Test
    @DisplayName("저장 데이터 덮어쓰기 성공")
    void updateSave_success() throws Exception {
        Long documentId = 1L;
        Long saveId = 1L;

        when(saveService.updateSave(dto, request)).thenReturn(new SaveUpdateResponse(
                OffsetDateTime.now()));

        mockMvc.perform(
                        put("/api/document/{documentId}/save/{saveId}", documentId, saveId)
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
        mockMvc.perform(
                        put("/api/document/{documentId}/save/{saveId}", documentId, saveId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(MethodArgumentTypeMismatchException.class,
                        result.getResolvedException()))
                .andDo(print());
    }

    @Test
    @DisplayName("getSave 성공")
    void getSave_success() throws Exception {
        Long documentId = 1L;
        Long saveId = 1L;

        when(saveService.getSave(dto)).thenReturn(new SaveGetResponse(
                OffsetDateTime.now(), data));

        mockMvc.perform(
                        get("/api/document/{documentId}/save/{saveId}", documentId, saveId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].text1").value("Key features"))
                .andExpect(jsonPath("$.content[1].text2").value("Key features"))
                .andDo(print());
    }

    @ParameterizedTest
    @DisplayName("path variable 이 비정상일 경우 예외 발생")
    @CsvSource({
            "abc,1",      // invalid documentId
            "1,xyz",      // invalid saveId
            "abc,xyz",    // both invalid
    })
    void getSave_fail_invalidPathVariables(String documentId, String saveId) throws Exception {
        mockMvc.perform(
                        put("/api/document/{documentId}/save/{saveId}", documentId, saveId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(MethodArgumentTypeMismatchException.class,
                        result.getResolvedException()))
                .andDo(print());
    }
}