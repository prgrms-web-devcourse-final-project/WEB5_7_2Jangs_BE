package io.ejangs.docsa.domain.doc.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.doc.api.DocController;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.*;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(DocController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocControllerUnitTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocService docService;

    @Test
    @DisplayName("문서 생성 성공 컨트롤러 테스트")
    void createDocSuccess() throws Exception {

        //given
        DocTitleRequest request = new DocTitleRequest("적당한 길이의 제목");
        Long documentId = 1L;

        //when, then
        when(docService.create(any(DocTitleRequest.class), anyLong()))
                .thenReturn(new DocCreateResponse(documentId));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .param("userId", "1")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(documentId))
                .andDo(print());
    }

    @Test
    @DisplayName("문서 생성 - 문서 제목이 빈값이면 400반환")
    void createDocFailByBlankTitle() throws Exception {
        DocTitleRequest request = new DocTitleRequest("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("문서제목을 입력해주세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("문서 리스트 조회 컨트롤러 테스트 - 사이드바")
    void getSimpleDocList() throws Exception {
        // given
        Long userId = 1L;

        List<DocListSimpleResponse> responseList = List.of(
                new DocListSimpleResponse(
                        1L,
                        "마이크로소프트",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1)
                ),
                new DocListSimpleResponse(
                        2L,
                        "구글",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(2)
                )
        );

        when(docService.getSimpleList(anyLong())).thenReturn(responseList);

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
    @DisplayName("문서 제목 수정 성공")
    void updateDocTitleSuccess() throws Exception {
        //given
        Long userId = 1L;
        Long docId = 1L;
        String newTitle = "new title";

        DocTitleRequest request = new DocTitleRequest(newTitle);

        DocTitleUpdateResponse response = new DocTitleUpdateResponse(
                docId,
                newTitle,
                LocalDateTime.now()
        );

        when(docService.updateTitle(userId, docId, request)).thenReturn(response);

        //when & then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/document/" + docId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId))
                .andExpect(jsonPath("$.title").value(newTitle));
    }

    @Test
    @DisplayName("문서 제목 중복 - 400 예외 반환")
    void updateDocTitleFailByDuplicationTitle() throws Exception {
        // given
        Long userId = 1L;
        Long documentId = 10L;
        String title = "중복된 제목";

        DocTitleRequest request = new DocTitleRequest(title);

        when(docService.updateTitle(userId, documentId, request))
                .thenThrow(new CustomException(DocErrorCode.TITLE_DUPLICATION));

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/document/{documentId}", documentId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("문서 제목 수정 - 문서 제목이 50자 초과")
    void updateDocTitleFailByTooLongTitle() throws Exception {
        //given
        Long userId = 1L;
        Long documentId = 10L;
        String tooLongTitle = "ThisTitleIsDefinitelyLongerThanFiftyCharactersInTotalLength!";
        DocTitleRequest request = new DocTitleRequest(tooLongTitle);

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/document/{documentId}", documentId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("문서제목은 50자를 초과 할 수 없습니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("기록 그래프 조회 성공")
    void getGraphSuccess() throws Exception {
        // given
        Long docId = 1L;

        CommitDto commit = new CommitDto(11L, 101L, "커밋1", "설명1", LocalDateTime.now());
        EdgeDto edge = new EdgeDto(11L, 12L);
        BranchDto branch = new BranchDto(101L, "main", LocalDateTime.now(), null, 11L, 13L, null);

        CommitGraphResponse response = new CommitGraphResponse(
                "문서 제목",
                List.of(commit),
                List.of(edge),
                List.of(branch)
        );

        when(docService.getGraph(docId)).thenReturn(response);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{documentId}/graph", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("문서 제목"))
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.branches").isArray())
                .andExpect(jsonPath("$.edges").isArray())
                .andDo(print());
    }

    @Test
    @DisplayName("기록 그래프 조회 실패 - 문서 없음")
    void getGraphFailByNotFound() throws Exception {
        // given
        Long docId = 999L;
        when(docService.getGraph(docId))
                .thenThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{documentId}/graph", docId))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }



}

