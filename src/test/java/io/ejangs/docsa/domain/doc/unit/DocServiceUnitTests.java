package io.ejangs.docsa.domain.doc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto.RecentType;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.util.PageableFactory;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocServiceUnitTests {

    @InjectMocks
    private DocService docService;

    @Mock
    private DocRepository docRepository;

    @Mock
    private DocListAssembler docListAssembler;

    @Test
    @DisplayName("사이드바 문서 목록 조회 성공 테스트")
    void getSimpleDocumentListSuccess() throws Exception {
        // given
        Long userId = 1L;
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        List<Doc> content = DocTestUtils.createDocumentListForUnitTest(2, user);
        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);
        Page<Doc> docs = new PageImpl<>(content, pageable, content.size());

        List<DocListSimpleResponse> expectedResponses = List.of(
                new DocListSimpleResponse(
                        1L,
                        "테스트 문서 1",
                        LocalDateTime.of(2025, 7, 16, 2, 0),
                        LocalDateTime.of(2025, 7, 16, 2, 0),
                        new RecentActivityDto(RecentType.SAVE, 10L)
                ),
                new DocListSimpleResponse(
                        2L,
                        "테스트 문서 2",
                        LocalDateTime.of(2025, 7, 16, 3, 0),
                        LocalDateTime.of(2025, 7, 16, 3, 0),
                        new RecentActivityDto(RecentType.COMMIT, 200L)
                )
        );
        Page<DocListSimpleResponse> dummyPage = new PageImpl<>(expectedResponses, pageable,
                expectedResponses.size());

        when(docRepository.findAllByUserId(userId, pageable)).thenReturn(docs);
        when(docListAssembler.assembleDocListSimple(docs)).thenReturn(dummyPage);

        // when
        Page<DocListSimpleResponse> page = docService.getSimpleList(userId, pageable);
        List<DocListSimpleResponse> result = page.getContent();

        // then
        assertEquals(2, result.size());

        assertEquals("테스트 문서 1", result.get(0).title());
        assertEquals(RecentType.SAVE, result.get(0).recent().recentType());
        assertEquals(10L, result.get(0).recent().recentTypeId());

        assertEquals("테스트 문서 2", result.get(1).title());
        assertEquals(RecentType.COMMIT, result.get(1).recent().recentType());
        assertEquals(200L, result.get(1).recent().recentTypeId());

        verify(docRepository).findAllByUserId(userId, pageable);
        verify(docListAssembler).assembleDocListSimple(docs);
    }

    @Test
    @DisplayName("검색 키워드를 포함한 제목을 가진 문서가 있으면 검색 결과를 페이지로 반환한다.")
    void searchDocTitleSuccess() throws Exception {
        // given
        String keyword = "문서 1";
        Long userId = 1L;
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        // 전체 문서 생성
        List<Doc> allDocs = DocTestUtils.createDocumentListForUnitTest(100, user);

        // 키워드 필터링 + 정렬
        List<Doc> filtered = allDocs.stream()
                .filter(d -> d.getTitle().contains(keyword))
                .sorted(Comparator.comparing(Doc::getUpdatedAt).reversed())
                .toList();

        Pageable pageable = PageableFactory.create("updatedAt", "desc", 0, 10);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Doc> pagedDocs = filtered.subList(start, end);

        Page<Doc> docsPage = new PageImpl<>(pagedDocs, pageable, filtered.size());
        Page<DocListResponse> responsesPage = DocTestUtils.convertToDocListResponsePage(pagedDocs,
                pageable);

        when(docRepository.searchDocByTitle(keyword, userId, pageable)).thenReturn(docsPage);
        when(docListAssembler.assembleDocList(docsPage)).thenReturn(responsesPage);

        // when
        Page<DocListResponse> page = docService.searchList(userId, keyword, pageable);
        List<DocListResponse> result = page.getContent();

        // then
        assertEquals(10, result.size());
        assertEquals("테스트 문서 100", result.get(0).title());
        assertEquals("테스트 문서 19", result.get(1).title());
        assertEquals("테스트 문서 11", result.getLast().title());

        verify(docRepository).searchDocByTitle(keyword, userId, pageable);
        verify(docListAssembler).assembleDocList(docsPage);
    }

    @Test
    @DisplayName("문서 제목 수정 성공 테스트")
    void updateDocTitleSuccess() throws Exception {
        //given
        Long userId = 1L;

        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Long docId = 10L;
        String newTitle = "new title";

        Doc doc = Doc.builder().title("old title").user(user).build();
        ReflectionTestUtils.setField(doc, "id", docId);
        ReflectionTestUtils.setField(doc, "updatedAt", LocalDateTime.now());

        DocTitleRequest request = new DocTitleRequest(newTitle);

        DocTitleUpdateResponse response =
                new DocTitleUpdateResponse(docId, newTitle, LocalDateTime.now());

        when(docRepository.existsByUserIdAndTitle(userId, newTitle)).thenReturn(false);
        when(docRepository.getDocByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        //when
        DocTitleUpdateResponse result = docService.updateTitle(userId, docId, request);

        //then
        verify(docRepository).existsByUserIdAndTitle(userId, newTitle);
        verify(docRepository).getDocByIdAndUserId(docId, userId);
        assertEquals(newTitle, doc.getTitle());
        assertEquals(response.id(), doc.getId());
        assertEquals(response.title(), result.title());
    }

    @Test
    @DisplayName("문서 제목 수정 실패 테스트 - 문서이름 중복")
    void updateDocTitleFailByDuplicateTitle() throws Exception {
        // given
        Long userId = 1L;
        Long docId = 10L;
        String duplicateTitle = "중복된 제목";
        DocTitleRequest request = new DocTitleRequest(duplicateTitle);

        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Doc doc = Doc.builder().title("기존 제목").user(user).build();
        ReflectionTestUtils.setField(doc, "id", docId);

        when(docRepository.getDocByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));
        when(docRepository.existsByUserIdAndTitle(userId, duplicateTitle)).thenReturn(true);

        // when & then
        assertThrows(CustomException.class, () -> docService.updateTitle(userId, docId, request));
    }

    @Test
    @DisplayName("문서 생성 후 커밋/간선 추가 - 그래프 조회 성공")
    void createDocAndGetGraphWithCommitsAndEdge() {
        // given
        Long userId = 1L;
        Long docId = 100L;
        Long branchId = 200L;
        Long commit1Id = 301L;
        Long commit2Id = 302L;
        Long edgeId = 401L;

        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Doc doc = Doc.builder().title("그래프 문서").user(user).build();
        ReflectionTestUtils.setField(doc, "id", docId);

        Branch branch = Branch.builder().name("main").doc(doc).build();
        ReflectionTestUtils.setField(branch, "id", branchId);

        Commit commit1 = Commit.builder().commitMongoId("c1").title("커밋1").description("desc")
                .branch(branch).build();
        Commit commit2 = Commit.builder().commitMongoId("c2").title("커밋2").description("desc")
                .branch(branch).build();
        ReflectionTestUtils.setField(commit1, "id", commit1Id);
        ReflectionTestUtils.setField(commit2, "id", commit2Id);
        ReflectionTestUtils.setField(commit1, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(commit2, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(branch, "createdAt", LocalDateTime.now());

        Edge edge = Edge.builder().doc(doc).prevCommit(commit1).nextCommit(commit2).build();
        ReflectionTestUtils.setField(edge, "id", edgeId);

        when(docRepository.existsByIdAndUserId(docId, userId)).thenReturn(true);
        when(docRepository.findByIdWithBranchesAndEdges(docId)).thenReturn(Optional.of(doc));

        // when
        CommitGraphResponse graph = docService.getGraph(userId, docId);

        // then
        assertEquals("그래프 문서", graph.title());
        assertEquals(2, graph.commits().size());
        assertEquals(1, graph.edges().size());
        assertEquals(1, graph.branches().size());
    }


    @Test
    @DisplayName("문서 그래프 조회 실패 - 문서 없음")
    void getGraphFailByNotFound() {
        // given
        Long docId = 999L;
        Long userId = 1L;
        when(docRepository.existsByIdAndUserId(docId, userId)).thenReturn(true);
        when(docRepository.findByIdWithBranchesAndEdges(docId)).thenReturn(Optional.empty());

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> docService.getGraph(userId, docId));

        assertEquals(DocErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

}
