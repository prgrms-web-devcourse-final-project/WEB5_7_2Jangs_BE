package io.ejangs.docsa.domain.doc.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.dto.graph.GraphBranchDto;
import io.ejangs.docsa.domain.doc.dto.graph.GraphCommitDto;
import io.ejangs.docsa.domain.doc.api.DocController;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto.RecentType;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.save.util.PageableFactory;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.security.WithCustomMockUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(DocController.class)
@WithCustomMockUser
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
        Long saveId = 2L;

        //when, then
        when(docService.create(any(DocTitleRequest.class), anyLong()))
                .thenReturn(new DocCreateResponse(documentId, saveId));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.saveId").value(saveId))
                .andDo(print());
    }

    @Test
    @DisplayName("문서 생성 - 문서 제목이 빈값이면 400반환")
    void createDocFailByBlankTitle() throws Exception {
        DocTitleRequest request = new DocTitleRequest("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
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
        List<DocListSimpleResponse> content = List.of(
                new DocListSimpleResponse(
                        1L,
                        "마이크로소프트",
                        LocalDateTime.of(2025, 7, 6, 12, 0),
                        LocalDateTime.of(2025, 7, 7, 9, 0),
                        new RecentActivityDto(RecentType.SAVE, 10L)
                ),
                new DocListSimpleResponse(
                        2L,
                        "구글",
                        LocalDateTime.of(2025, 6, 28, 15, 30),
                        LocalDateTime.of(2025, 7, 1, 10, 45),
                        new RecentActivityDto(RecentType.COMMIT, 11L)
                )
        );

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);

        Page<DocListSimpleResponse> responseList = new PageImpl<>(
                content,
                PageRequest.of(0, 10),
                content.size()
        );

        when(docService.getSimpleList(anyLong(), any(Pageable.class))).thenReturn(responseList);

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/sidebar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("마이크로소프트"))
                .andExpect(jsonPath("$.content[0].recent.recentType").value("SAVE"))
                .andExpect(jsonPath("$.content[0].recent.recentTypeId").value(10))
                .andExpect(jsonPath("$.content[1].title").value("구글"))
                .andExpect(jsonPath("$.content[1].recent.recentType").value("COMMIT"))
                .andExpect(jsonPath("$.content[1].recent.recentTypeId").value(11))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("문서 제목 수정 - 문서 제목이 50자 초과")
    void updateDocTitleFailByTooLongTitle() throws Exception {
        //given
        Long documentId = 10L;
        String tooLongTitle = "ThisTitleIsDefinitelyLongerThanFiftyCharactersInTotalLength!";
        DocTitleRequest request = new DocTitleRequest(tooLongTitle);

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/document/{documentId}", documentId)
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
        Long userId = 1L;

        GraphCommitDto commit = new GraphCommitDto(11L, 101L, "커밋1", "설명1", LocalDateTime.now());
        GraphEdgeDto edge = new GraphEdgeDto(11L, 12L);
        GraphBranchDto
                branch = new GraphBranchDto(101L, "main", LocalDateTime.now(), null, 11L, 13L, null);

        CommitGraphResponse response = new CommitGraphResponse(
                "문서 제목",
                List.of(commit),
                List.of(edge),
                List.of(branch)
        );

        when(docService.getGraph(userId, docId)).thenReturn(response);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{docId}/graph", docId))
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
        Long userId = 1L;
        when(docService.getGraph(userId, docId))
                .thenThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{docId}/graph", docId))
                .andExpect(status().isNotFound())
                .andDo(print());
    }
}

