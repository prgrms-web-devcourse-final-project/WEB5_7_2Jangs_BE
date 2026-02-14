package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.dto.graph.GraphBranchDto;
import io.ejangs.docsa.domain.doc.dto.graph.GraphCommitDto;
import io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleOnlyResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.doc.util.GraphMapper;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final BranchRepository branchRepository;
    private final CommitRepository commitRepository;
    private final EdgeRepository edgeRepository;

    private final DocQueryService docQueryService;
    private final DocCreateOrchestrator docCreateOrchestrator;

    private final DocListAssembler docListAssembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final ApplicationEventPublisher eventPublisher;

    public DocCreateResponse create(DocTitleRequest request, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        String title = request.title();
        docQueryService.checkTitleDuplicate(userId, title);
        return docCreateOrchestrator.create(title, user);
    }

    @Transactional(readOnly = true)
    public Page<DocSimplePageResponse> getSimplePage(Long userId, Pageable pageable) {
        Page<Doc> docs = docQueryService.getDocPageByUserId(userId, pageable);
        return docListAssembler.assembleDocListSimple(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocPageResponse> getPage(Long userId, Pageable pageable) {
        Page<Doc> docs = docQueryService.getDocPageByUserId(userId, pageable);
        return docListAssembler.assembleDocList(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocPageResponse> searchList(Long userId, String keyword, Pageable pageable) {
        Page<Doc> docs = docQueryService.searchDoc(keyword,userId,pageable);
        return docListAssembler.assembleDocList(docs);
    }

    @Transactional
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId, DocTitleRequest request) {
        String title = request.title();

        Doc doc = docQueryService.getByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        docQueryService.checkTitleDuplicate(userId, title);
        doc.updateTitle(title);

        return DocMapper.toUpdateResponse(doc);
    }

    // 문서 조회시 그래프를 그리기 위한 응답 생성
    @Transactional(readOnly = true)
    public CommitGraphResponse getGraph(Long userId, Long documentId) {

        String docTitle = docQueryService.getByIdAndUserId(documentId, userId).getTitle();

        //  Branch, Commit, Edge 각각 별도 조회 (Projection 쿼리)
        List<GraphBranchDto> branches = branchRepository.findBranchesByDocId(documentId);
        if (branches.isEmpty()) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
        List<GraphCommitDto> commits = commitRepository.findCommitsByDocId(documentId);
        List<GraphEdgeDto> edges = edgeRepository.findEdgesByDocId(documentId);

        return GraphMapper.toCommitGraphResponse(docTitle, commits, edges, branches);
    }


    @Transactional
    public void delete(Long docId, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);
        List<Edge> edges = doc.getEdges();
        List<Branch> branches = doc.getBranches();

        MongoIdsDto docDeleteMongoIds = mongoIdsCollector.collectFrom(branches);
        edgeRepository.deleteAll(edges);

        user.removeDocument(doc);

        log.warn("[MONGO] deleteDocument");
        eventPublisher.publishEvent(docDeleteMongoIds);
    }

}
