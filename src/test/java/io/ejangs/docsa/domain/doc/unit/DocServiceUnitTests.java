package io.ejangs.docsa.domain.doc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.app.create.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto.RecentType;
import io.ejangs.docsa.domain.doc.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.doc.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.doc.dto.graph.EdgeDto;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.GraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.save.util.PageableFactory;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocServiceUnitTests {

    @InjectMocks
    private DocService docService;

    @Mock
    private DocRepository docRepository;

    @Mock
    private DocQueryService docQueryService;

    @Mock
    private DocCreateOrchestrator docCreateOrchestrator;

    @Mock
    private DocListAssembler docListAssembler;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchQueryService branchQueryService;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private MongoIdsCollector mongoIdsCollector;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("문서 생성 성공 - QueryService 검증 후 Orchestrator 호출(CQRS 분리)")
    void createDoc_delegatesToQueryServiceAndOrchestrator() {
        Long userId = 1L;
        String title = "새 문서";
        DocTitleRequest request = new DocTitleRequest(title);
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);
        DocCreateResponse expected = new DocCreateResponse(10L, 100L);

        when(docQueryService.getUserOrThrow(userId)).thenReturn(user);
        when(docCreateOrchestrator.create(title, user)).thenReturn(expected);

        DocCreateResponse result = docService.create(request, userId);

        assertEquals(expected.id(), result.id());
        assertEquals(expected.saveId(), result.saveId());
        verify(docQueryService).getUserOrThrow(userId);
        verify(docQueryService).checkTitleDuplicate(userId, title);
        verify(docCreateOrchestrator).create(title, user);
        verifyNoInteractions(docRepository, branchRepository, commitRepository, edgeRepository);
    }

    @Test
    @DisplayName("문서 생성 실패 - 제목 중복이면 Orchestrator 호출 안함")
    void createDoc_fail_duplicateTitle() {
        Long userId = 1L;
        String title = "중복 문서";
        DocTitleRequest request = new DocTitleRequest(title);
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        when(docQueryService.getUserOrThrow(userId)).thenReturn(user);
        doThrow(new CustomException(DocErrorCode.TITLE_DUPLICATION))
                .when(docQueryService).checkTitleDuplicate(userId, title);

        CustomException exception = assertThrows(CustomException.class, () -> docService.create(request, userId));

        assertEquals(DocErrorCode.TITLE_DUPLICATION, exception.getErrorCode());
        verifyNoInteractions(docCreateOrchestrator);
    }

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

        List<DocSimplePageResponse> expectedResponses = List.of(
                new DocSimplePageResponse(
                        1L,
                        "테스트 문서 1",
                        LocalDateTime.of(2025, 7, 16, 2, 0),
                        LocalDateTime.of(2025, 7, 16, 2, 0),
                        new RecentActivityDto(RecentType.SAVE, 10L)
                ),
                new DocSimplePageResponse(
                        2L,
                        "테스트 문서 2",
                        LocalDateTime.of(2025, 7, 16, 3, 0),
                        LocalDateTime.of(2025, 7, 16, 3, 0),
                        new RecentActivityDto(RecentType.COMMIT, 200L)
                )
        );
        Page<DocSimplePageResponse> dummyPage = new PageImpl<>(expectedResponses, pageable,
                expectedResponses.size());

        when(docQueryService.getPageByUserId(userId, pageable)).thenReturn(docs);
        when(docListAssembler.assembleDocListSimple(docs)).thenReturn(dummyPage);

        // when
        Page<DocSimplePageResponse> page = docService.getSimplePage(userId, pageable);
        List<DocSimplePageResponse> result = page.getContent();

        // then
        assertEquals(2, result.size());

        assertEquals("테스트 문서 1", result.get(0).title());
        assertEquals(RecentType.SAVE, result.get(0).recent().recentType());
        assertEquals(10L, result.get(0).recent().recentTypeId());

        assertEquals("테스트 문서 2", result.get(1).title());
        assertEquals(RecentType.COMMIT, result.get(1).recent().recentType());
        assertEquals(200L, result.get(1).recent().recentTypeId());

        verify(docQueryService).getPageByUserId(userId, pageable);
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
        Page<DocPageResponse> responsesPage = DocTestUtils.convertToDocListResponsePage(pagedDocs,
                pageable);

        when(docQueryService.searchByTitle(keyword, userId, pageable)).thenReturn(docsPage);
        when(docListAssembler.assembleDocList(docsPage)).thenReturn(responsesPage);

        // when
        Page<DocPageResponse> page = docService.searchList(userId, keyword, pageable);
        List<DocPageResponse> result = page.getContent();

        // then
        assertEquals(10, result.size());
        assertEquals("테스트 문서 100", result.get(0).title());
        assertEquals("테스트 문서 19", result.get(1).title());
        assertEquals("테스트 문서 11", result.getLast().title());

        verify(docQueryService).searchByTitle(keyword, userId, pageable);
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

        when(docQueryService.getByIdAndUserId(docId, userId)).thenReturn(doc);

        //when
        DocTitleUpdateResponse result = docService.updateTitle(userId, docId, request);

        //then
        verify(docQueryService).checkTitleDuplicate(userId, newTitle);
        verify(docQueryService).getByIdAndUserId(docId, userId);
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

        when(docQueryService.getByIdAndUserId(docId, userId)).thenReturn(doc);
        doThrow(new CustomException(DocErrorCode.TITLE_DUPLICATION))
                .when(docQueryService).checkTitleDuplicate(userId, duplicateTitle);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> docService.updateTitle(userId, docId, request));

        assertEquals(DocErrorCode.TITLE_DUPLICATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("그래프 조회 성공")
    void getGraph_shouldReturnGraphResponse_whenDocExists() {
        Long userId = 1L;
        Long docId = 10L;
        String docTitle = "Test Document";

        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Doc doc = Doc.builder().title(docTitle).user(user).build();
        ReflectionTestUtils.setField(doc, "id", docId);

        // Mock 문서 제목 조회
        when(docQueryService.getByIdAndUserId(docId, userId))
                .thenReturn(doc);

        // Mock Branch, Commit, Edge 리스트
        LocalDateTime now = LocalDateTime.now();
        List<BranchGraphDto> branches = List.of(
                new BranchGraphDto(1L, "main", now, null, null, null, null)
        );
        List<CommitGraphDto> commits = List.of(
                new CommitGraphDto(100L, 1L, "Initial Commit", "desc", now)
        );
        List<EdgeDto> edges = List.of(
                new EdgeDto(100L, 101L)
        );

        when(branchQueryService.getBranchGraphList(docId)).thenReturn(branches);
        when(commitRepository.findCommitsByDocId(docId)).thenReturn(commits);
        when(edgeRepository.findEdgesByDocId(docId)).thenReturn(edges);

        GraphResponse response = docService.getGraph(userId, docId);

        assertEquals(docTitle, response.title());
        assertEquals(branches, response.branches());
        assertEquals(commits, response.commits());
        assertEquals(edges, response.edges());
    }
}
