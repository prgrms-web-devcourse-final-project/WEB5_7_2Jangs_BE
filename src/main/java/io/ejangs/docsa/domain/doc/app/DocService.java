package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.doc.app.create.DocCreateOrchestrator;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.doc.readmodel.dto.payload.DocCreatedPayload;
import io.ejangs.docsa.domain.doc.readmodel.util.DocPayloadFactory;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.edge.entity.Edge;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.edge.util.GraphMapper;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.outbox.event.app.DomainEventOutboxPublisher;
import io.ejangs.docsa.global.outbox.event.model.AggregateType;
import io.ejangs.docsa.global.outbox.event.model.DomainEventType;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteJobEnqueuer;
import io.ejangs.docsa.global.outbox.mongo.util.MongoIdsCollector;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocQueryService docQueryService;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final EdgeService edgeService;

    private final DocCreateOrchestrator docCreateOrchestrator;
    private final MongoDeleteJobEnqueuer mongoDeleteJobEnqueuer;

    private final DocListAssembler docListAssembler;
    private final MongoIdsCollector mongoIdsCollector;

    private final DomainEventOutboxPublisher domainEventOutboxPublisher;

    public DocCreateResponse create(DocTitleRequest request, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        String title = request.title();
        docQueryService.checkTitleDuplicate(userId, title);
        return docCreateOrchestrator.create(title, user);
    }

    @Transactional(readOnly = true)
    public Page<DocSimplePageResponse> getSimplePage(Long userId, Pageable pageable) {
        Page<Doc> docs = docQueryService.getPageByUserId(userId, pageable);
        return docListAssembler.assembleDocListSimple(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocPageResponse> getPage(Long userId, Pageable pageable) {
        Page<Doc> docs = docQueryService.getPageByUserId(userId, pageable);
        return docListAssembler.assembleDocList(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocPageResponse> searchList(Long userId, String keyword, Pageable pageable) {
        Page<Doc> docs = docQueryService.searchByTitle(keyword, userId, pageable);
        return docListAssembler.assembleDocList(docs);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId, DocTitleRequest request) {
        String title = request.title();

        Doc doc = docQueryService.getByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        docQueryService.checkTitleDuplicate(userId, title);
        doc.updateTitle(title);
        doc.updateTimestamp();

        domainEventOutboxPublisher.publish(DomainEventType.DOC_TITLE_CHANGED, AggregateType.DOC,
                doc.getId(), DocPayloadFactory.titleChanged(doc));

        return DocMapper.toUpdateResponse(doc);
    }

    // 문서 조회시 그래프를 그리기 위한 응답 생성
    @Transactional(readOnly = true)
    public GraphResponse getGraph(Long userId, Long documentId) {

        String docTitle = docQueryService.getByIdAndUserId(documentId, userId).getTitle();

        //  Branch, Commit, Edge 각각 별도 조회 (Projection 쿼리)
        List<BranchGraphDto> branches = branchQueryService.getBranchGraphList(documentId);
        if (branches.isEmpty()) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
        List<CommitGraphDto> commits = commitQueryService.getCommitGraphList(documentId);
        List<EdgeDto> edges = edgeService.getEdgeDtoByDocId(documentId);

        return GraphMapper.toCommitGraphResponse(docTitle, commits, edges, branches);
    }


    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);
        List<Edge> edges = doc.getEdges();
        List<Branch> branches = doc.getBranches();

        MongoIdsDto docDeleteMongoIds = mongoIdsCollector.collectFrom(branches);
        edgeService.deleteAll(edges);

        user.removeDocument(doc);

        mongoDeleteJobEnqueuer.enqueueDocDeletion(docId, docDeleteMongoIds);
    }

}
