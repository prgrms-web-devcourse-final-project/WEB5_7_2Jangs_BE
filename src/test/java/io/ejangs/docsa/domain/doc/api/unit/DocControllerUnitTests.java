package io.ejangs.docsa.domain.doc.api.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.doc.api.DocController;
import io.ejangs.docsa.domain.doc.app.DocCommandService;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;
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
    private DocCommandService docCommandService;

    @MockitoBean
    private DocQueryService docQueryService;

    @Test
    @DisplayName("문서 생성 성공 컨트롤러 테스트")
    void createDocSuccess() throws Exception {

        //given
        DocTitleRequest request = new DocTitleRequest("적당한 길이의 제목");
        Long documentId = 1L;
        Long saveId = 2L;

        //when, then
        when(docCommandService.create(any(DocTitleRequest.class), anyLong()))
                .thenReturn(new DocCreateResponse(documentId, saveId));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.saveId").value(saveId));
    }

    @Test
    @DisplayName("문서 생성 - 문서 제목이 빈값이면 400반환")
    void createDocFailByBlankTitle() throws Exception {
        DocTitleRequest request = new DocTitleRequest("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("문서제목을 입력해주세요."));
    }

    @Test
    @DisplayName("문서 리스트 조회 컨트롤러 테스트 - 사이드바")
    void getSimpleDocList() throws Exception {
        // given
        List<DocSimplePageResponse> content = List.of(
                new DocSimplePageResponse(
                        1L,
                        "마이크로소프트",
                        LocalDateTime.of(2025, 7, 6, 12, 0),
                        LocalDateTime.of(2025, 7, 7, 9, 0),
                        10L
                ),
                new DocSimplePageResponse(
                        2L,
                        "구글",
                        LocalDateTime.of(2025, 6, 28, 15, 30),
                        LocalDateTime.of(2025, 7, 1, 10, 45),
                        11L
                )
        );

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);

        Page<DocSimplePageResponse> responseList = new PageImpl<>(
                content,
                PageRequest.of(0, 10),
                content.size()
        );

        when(docQueryService.getSimplePage(anyLong(), any(Pageable.class))).thenReturn(responseList);

        // when, then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/sidebar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("마이크로소프트"))
                .andExpect(jsonPath("$.content[0].recentSaveId").value(10))
                .andExpect(jsonPath("$.content[1].title").value("구글"))
                .andExpect(jsonPath("$.content[1].recentSaveId").value(11));
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

        when(docCommandService.updateTitle(userId, docId, request)).thenReturn(response);

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

        when(docCommandService.updateTitle(userId, documentId, request))
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
                .andExpect(jsonPath("$.message").value("문서제목은 50자를 초과 할 수 없습니다."));
    }

    @Test
    @DisplayName("기록 그래프 조회 성공")
    void getGraphSuccess() throws Exception {
        // given
        Long docId = 1L;
        Long userId = 1L;

        CommitGraphDto commit = new CommitGraphDto(11L, 101L, "커밋1", "설명1", LocalDateTime.now());
        EdgeDto edge = new EdgeDto(11L, 12L);
        BranchGraphDto
                branch = new BranchGraphDto(101L, "main", LocalDateTime.now(), null, null, 11L, 13L, null);

        GraphResponse response = new GraphResponse(
                "문서 제목",
                List.of(commit),
                List.of(edge),
                List.of(branch)
        );

        when(docQueryService.getGraph(userId, docId)).thenReturn(response);

        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{docId}/graph", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("문서 제목"))
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.branches").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    @DisplayName("기록 그래프 조회 실패 - 문서 없음")
    void getGraphFailByNotFound() throws Exception {
        // given
        Long docId = 999L;
        Long userId = 1L;
        when(docQueryService.getGraph(userId, docId))
                .thenThrow(new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
        // when & then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/document/{docId}/graph", docId))
                .andExpect(status().isNotFound());
    }
}
