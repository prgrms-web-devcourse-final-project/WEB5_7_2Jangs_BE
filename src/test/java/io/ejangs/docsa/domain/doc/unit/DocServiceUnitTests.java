package io.ejangs.docsa.domain.doc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.EdgeDto;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto.RecentType;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocServiceUnitTests {

    @InjectMocks
    private DocService docService;

    @Mock
    private DocRepository docRepository;

    @Test
    @DisplayName("사이드바 문서 목록 조회 성공 테스트")
    void getSimpleDocumentListSuccess() throws Exception {

        // given
        Long userId = 1L;
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        Long docId = 10L;
        List<Doc> docs = DocTestUtils.createDocumentList(2, user);

        when(docRepository.findAllByUserId(userId)).thenReturn(docs);

        // when
        List<DocListSimpleResponse> result = docService.getSimpleList(userId);

        // then
        assertEquals(2, result.size());
        assertEquals("테스트 문서 1", result.getFirst().title());

        assertEquals(RecentType.SAVE, result.getFirst().recent().recentType());
        assertEquals(10L, result.getFirst().recent().recentTypeId());

        assertEquals(RecentType.COMMIT, result.getLast().recent().recentType());
        assertEquals(200L, result.getLast().recent().recentTypeId());

        verify(docRepository).findAllByUserId(userId);
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
    @DisplayName("그래프 조회 성공")
    void getGraph_shouldReturnGraphResponse_whenDocExists() {
        Long userId = 1L;
        Long docId = 10L;

        when(docRepository.existsByIdAndUserId(docId, userId)).thenReturn(true);

        // Mock 문서 제목 조회
        when(docRepository.findTitleOnlyById(docId))
                .thenReturn(Optional.of(new DocTitleOnlyResponse(docId, "Test Document")));

        // Mock Branch, Commit, Edge 리스트
        OffsetDateTime now = OffsetDateTime.now();
        List<BranchDto> branches = List.of(
                new BranchDto(1L, "main", now, null, null, null, null)
        );
        List<CommitDto> commits = List.of(
                new CommitDto(100L, 1L, "Initial Commit", "desc", now)
        );
        List<EdgeDto> edges = List.of(
                new EdgeDto(100L, 101L)
        );

        when(docRepository.findBranchesByDocId(docId)).thenReturn(branches);
        when(docRepository.findCommitsByDocId(docId)).thenReturn(commits);
        when(docRepository.findEdgesByDocId(docId)).thenReturn(edges);

        CommitGraphResponse response = docService.getGraph(userId, docId);

        assertEquals("Test Document", response.title());
        assertEquals(branches, response.branches());
        assertEquals(commits, response.commits());
        assertEquals(edges, response.edges());
    }
}
