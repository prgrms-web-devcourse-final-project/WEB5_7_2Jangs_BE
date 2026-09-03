package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.app.create.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.util.MongoIdsCollector;
import java.util.List;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationStart;
import io.ejangs.docsa.global.saga.create.app.MongoCreatePlanFactory;
import io.ejangs.docsa.global.saga.create.app.MongoCreateRequestHasher;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocCommandService {

    private final DocReader docReader;
    private final EdgeService edgeService;

    private final DocCreateOrchestrator docCreateOrchestrator;
    private final MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    private final MongoIdsCollector mongoIdsCollector;

    private final DomainEventOutboxPublisher domainEventOutboxPublisher;
    private final MongoCreateOperationService mongoCreateOperationService;
    private final MongoCreatePlanFactory mongoCreatePlanFactory;
    private final MongoCreateRequestHasher mongoCreateRequestHasher;

    public DocCreateResponse create(DocTitleRequest request, Long userId, String operationId) {
        String requestHash = mongoCreateRequestHasher.hash(
                List.of(MongoCreateOperationType.DOC, userId, request)
        );
        MongoCreateOperationStart existing = mongoCreateOperationService.findExisting(
                operationId,
                userId,
                MongoCreateOperationType.DOC,
                requestHash
        ).orElse(null);
        if (existing != null) {
            return new DocCreateResponse(existing.resultEntityId(), existing.resultSaveId());
        }

        User user = docReader.getUserOrThrow(userId);
        String title = request.title();
        docReader.checkTitleDuplicate(userId, title);
        MongoIdsDto plan = mongoCreatePlanFactory.singleSaveContent();
        return docCreateOrchestrator.create(title, user, operationId, requestHash, plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId, DocTitleRequest request) {
        String title = request.title();

        Doc doc = docReader.getByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        docReader.checkTitleDuplicate(userId, title);
        doc.updateTitle(title);
        doc.updateTimestamp();

        domainEventOutboxPublisher.publish(DomainEventType.DOC_TITLE_CHANGED, AggregateType.DOC,
                doc.getId(), DocPayloadFactory.titleChanged(doc));

        return DocMapper.toUpdateResponse(doc);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId, Long userId) {
        User user = docReader.getUserOrThrow(userId);
        Doc doc = docReader.getByIdAndUserId(docId, userId);
        List<Edge> edges = doc.getEdges();
        List<Branch> branches = doc.getBranches();

        MongoIdsDto docDeleteMongoIds = mongoIdsCollector.collectFrom(branches);
        edgeService.deleteAll(edges);

        user.removeDocument(doc);

        domainEventOutboxPublisher.publish(DomainEventType.DOC_DELETED, AggregateType.DOC, docId,
                DocPayloadFactory.deleted(docId));
        mongoDeleteJobEnqueuer.enqueueDocDeletion(docId, docDeleteMongoIds);
    }

}
