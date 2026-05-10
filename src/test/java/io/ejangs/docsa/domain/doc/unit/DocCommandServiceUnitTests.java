package io.ejangs.docsa.domain.doc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.doc.app.DocReader;
import io.ejangs.docsa.domain.doc.app.DocCommandService;
import io.ejangs.docsa.domain.doc.app.create.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.edge.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocTestUtils;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.outbox.mongo.util.MongoIdsCollector;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class DocCommandServiceUnitTests {

    @InjectMocks
    private DocCommandService docCommandService;

    @Mock
    private DocRepository docRepository;

    @Mock
    private DocReader docReader;

    @Mock
    private DocCreateOrchestrator docCreateOrchestrator;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private EdgeService edgeService;

    @Mock
    private MongoIdsCollector mongoIdsCollector;

    @Mock
    private DomainEventOutboxPublisher domainEventOutboxPublisher;

    @Test
    @DisplayName("문서 생성 성공 - QueryService 검증 후 Orchestrator 호출(CQRS 분리)")
    void createDoc_delegatesToQueryServiceAndOrchestrator() {
        Long userId = 1L;
        String title = "새 문서";
        DocTitleRequest request = new DocTitleRequest(title);
        User user = DocTestUtils.createUser();
        ReflectionTestUtils.setField(user, "id", userId);
        DocCreateResponse expected = new DocCreateResponse(10L, 100L);

        when(docReader.getUserOrThrow(userId)).thenReturn(user);
        when(docCreateOrchestrator.create(title, user)).thenReturn(expected);

        DocCreateResponse result = docCommandService.create(request, userId);

        assertEquals(expected.id(), result.id());
        assertEquals(expected.saveId(), result.saveId());
        verify(docReader).getUserOrThrow(userId);
        verify(docReader).checkTitleDuplicate(userId, title);
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

        when(docReader.getUserOrThrow(userId)).thenReturn(user);
        doThrow(new CustomException(DocErrorCode.TITLE_DUPLICATION))
                .when(docReader).checkTitleDuplicate(userId, title);

        CustomException exception = assertThrows(CustomException.class, () -> docCommandService.create(request, userId));

        assertEquals(DocErrorCode.TITLE_DUPLICATION, exception.getErrorCode());
        verifyNoInteractions(docCreateOrchestrator);
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

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(doc);

        //when
        DocTitleUpdateResponse result = docCommandService.updateTitle(userId, docId, request);

        //then
        verify(docReader).checkTitleDuplicate(userId, newTitle);
        verify(docReader).getByIdAndUserId(docId, userId);
        verify(domainEventOutboxPublisher).publish(
                eq(DomainEventType.DOC_TITLE_CHANGED),
                eq(AggregateType.DOC),
                eq(docId),
                any()
        );
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

        when(docReader.getByIdAndUserId(docId, userId)).thenReturn(doc);
        doThrow(new CustomException(DocErrorCode.TITLE_DUPLICATION))
                .when(docReader).checkTitleDuplicate(userId, duplicateTitle);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> docCommandService.updateTitle(userId, docId, request));

        assertEquals(DocErrorCode.TITLE_DUPLICATION, exception.getErrorCode());
    }

}
