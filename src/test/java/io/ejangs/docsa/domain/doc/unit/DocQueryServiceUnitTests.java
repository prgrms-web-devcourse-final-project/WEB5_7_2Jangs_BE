package io.ejangs.docsa.domain.doc.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocQueryServiceUnitTests {

    @Mock
    private DocListReadModelRepository docListReadModelRepository;

    @Mock
    private DocReader docReader;

    @Mock
    private BranchQueryService branchQueryService;

    @Mock
    private CommitQueryService commitQueryService;

    @Mock
    private EdgeService edgeService;

    @InjectMocks
    private DocQueryService docQueryService;

    private final Long userId = 1L;
    private final Pageable pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(docQueryService, "cdnUrl", "https://cdn.test.invalid");
    }

    @Test
    @DisplayName("문서 목록 read model을 사이드바 응답으로 변환한다")
    void getSimplePage_success() {
        DocListReadModel model = readModel(1L, "문서 1", 10L, null, ThumbnailStatus.EMPTY);
        when(docListReadModelRepository.findByUserIdAndDeletedFalse(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(model), pageable, 1));

        Page<DocSimplePageResponse> result = docQueryService.getSimplePage(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        DocSimplePageResponse response = result.getContent().getFirst();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("문서 1");
        assertThat(response.recentSaveId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("문서 목록 read model을 목록 응답으로 변환한다")
    void getPage_success() {
        DocListReadModel model = readModel(1L, "문서 1", 10L, "thumb-1.webp", ThumbnailStatus.READY);
        when(docListReadModelRepository.findByUserIdAndDeletedFalse(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(model), pageable, 1));

        Page<DocPageResponse> result = docQueryService.getPage(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        DocPageResponse response = result.getContent().getFirst();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("문서 1");
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.test.invalid/thumb-1.webp");
        assertThat(response.thumbnailStatus()).isEqualTo(ThumbnailStatus.READY);
        assertThat(response.recentSaveId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("검색 키워드를 포함한 문서 목록 read model을 페이지로 반환한다")
    void searchList_success() {
        String keyword = "문서";
        DocListReadModel model = readModel(1L, "문서 1", 10L, null, ThumbnailStatus.EMPTY);
        when(docListReadModelRepository.findByUserIdAndDeletedFalseAndTitleContainingIgnoreCase(
                userId,
                keyword,
                pageable
        )).thenReturn(new PageImpl<>(List.of(model), pageable, 1));

        Page<DocPageResponse> result = docQueryService.searchList(userId, keyword, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).isEqualTo("문서 1");
    }

    @Test
    @DisplayName("그래프 조회 성공")
    void getGraph_success() {
        Long docId = 10L;
        String docTitle = "Test Document";
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);
        Doc doc = Doc.builder().title(docTitle).user(user).build();
        ReflectionTestUtils.setField(doc, "id", docId);
        LocalDateTime now = LocalDateTime.now();
        List<BranchGraphDto> branches = List.of(
                new BranchGraphDto(1L, "main", now, null, null, null, null, null)
        );
        List<CommitGraphDto> commits = List.of(
                new CommitGraphDto(100L, 1L, "Initial Commit", "desc", now)
        );
        List<EdgeDto> edges = List.of(new EdgeDto(100L, 101L));

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(doc);
        when(branchQueryService.getBranchGraphList(docId)).thenReturn(branches);
        when(commitQueryService.getCommitGraphList(docId)).thenReturn(commits);
        when(edgeService.getEdgeDtoByDocId(docId)).thenReturn(edges);

        GraphResponse response = docQueryService.getGraph(userId, docId);

        assertThat(response.title()).isEqualTo(docTitle);
        assertThat(response.branches()).isEqualTo(branches);
        assertThat(response.commits()).isEqualTo(commits);
        assertThat(response.edges()).isEqualTo(edges);
    }

    @Test
    @DisplayName("그래프 조회 시 브랜치가 없으면 예외가 발생한다")
    void getGraph_fail_branchNotFound() {
        Long docId = 10L;
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);
        Doc doc = Doc.builder().title("문서").user(user).build();

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(doc);
        when(branchQueryService.getBranchGraphList(docId)).thenReturn(List.of());

        assertThatThrownBy(() -> docQueryService.getGraph(userId, docId))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(BranchErrorCode.BRANCH_NOT_FOUND));
    }

    private DocListReadModel readModel(
            Long docId,
            String title,
            Long recentSaveId,
            String thumbnailObjectKey,
            ThumbnailStatus thumbnailStatus
    ) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        return DocListReadModel.create(
                new DocCreatedPayload(
                        docId,
                        userId,
                        title,
                        createdAt,
                        updatedAt,
                        recentSaveId,
                        thumbnailObjectKey,
                        thumbnailStatus
                ),
                docId
        );
    }
}
