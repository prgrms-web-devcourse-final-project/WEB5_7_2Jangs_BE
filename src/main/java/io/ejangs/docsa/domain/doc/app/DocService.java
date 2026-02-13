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
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocRepository docRepository;
    private final BranchRepository branchRepository;
    private final CommitRepository commitRepository;
    private final EdgeRepository edgeRepository;

    private final DocQueryService docQueryService;
    private final DocCreateSagaService docCreateSagaService;

    private final DocListAssembler docListAssembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final ApplicationEventPublisher eventPublisher;

    public DocCreateResponse create(DocTitleRequest request, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        String title = request.title();
        docQueryService.checkTitleDuplicate(userId, title);
        return docCreateSagaService.create(title, user);
    }

    @Transactional(readOnly = true)
    public Page<DocListSimpleResponse> getSimpleList(Long userId, Pageable pageable) {
        Page<Doc> docs = docRepository.findAllByUserId(userId, pageable);

        return docListAssembler.assembleDocListSimple(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocListResponse> getList(Long userId, Pageable pageable) {
        Page<Doc> docs = docRepository.findAllByUserId(userId, pageable);

        return docListAssembler.assembleDocList(docs);
    }

    @Transactional(readOnly = true)
    public Page<DocListResponse> searchList(Long userId, String keyword, Pageable pageable) {
        Page<Doc> docs = docRepository.searchDocByTitle(keyword, userId, pageable);

        return docListAssembler.assembleDocList(docs);
    }

    @Transactional
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId, DocTitleRequest request) {
        String title = request.title();

        Doc doc = getDocByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        docQueryService.checkTitleDuplicate(userId, title);
        doc.updateTitle(title);

        return DocMapper.toUpdateResponse(doc);
    }


    public Doc getDocByIdAndUserId(Long documentId, Long userId) {
        return docRepository.getDocByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

    public void checkDocByIdAndUserId(Long docId, Long userId) {
        if (!docRepository.existsByIdAndUserId(docId, userId)) {
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Doc getById(Long id) {
        return docRepository.findById(id)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

    // 문서 조회시 그래프를 그리기 위한 응답 생성
    @Transactional(readOnly = true)
    public CommitGraphResponse getGraph(Long userId, Long documentId) {

        checkDocByIdAndUserId(documentId, userId);

        // 문서 제목 조회
        String docTitle = getTitleOnlyById(documentId);

        //  Branch, Commit, Edge 각각 별도 조회 (Projection 쿼리)
        List<GraphBranchDto> branches = branchRepository.findBranchesByDocId(documentId);
        if (branches.isEmpty()) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
        List<GraphCommitDto> commits = commitRepository.findCommitsByDocId(documentId);
        List<GraphEdgeDto> edges = edgeRepository.findEdgesByDocId(documentId);

        return GraphMapper.toCommitGraphResponse(docTitle, commits, edges, branches);
    }

    private String getTitleOnlyById(Long documentId) {
        Optional<DocTitleOnlyResponse> optionalTitle = docRepository.findTitleOnlyById(documentId);

        if (optionalTitle.isEmpty()) {
            log.error("문서 ID {}의 제목 찾지 못함 (DocErrorCode.DOCUMENT_NOT_FOUND)", documentId);
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
        }

        return optionalTitle.get().title();
    }

    @Transactional
    public void delete(Long docId, Long userId) {
        User user = docQueryService.getUserOrThrow(userId);
        Doc doc = getDocByIdAndUserId(docId, userId);
        List<Edge> edges = doc.getEdges();
        List<Branch> branches = doc.getBranches();

        MongoIdsDto docDeleteMongoIds = mongoIdsCollector.collectFrom(branches);
        edgeRepository.deleteAll(edges);

        user.removeDocument(doc);

        log.warn("[MONGO] deleteDocument");
        eventPublisher.publishEvent(docDeleteMongoIds);
    }

}
