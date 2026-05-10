package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.app.BranchQueryService;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.readmodel.dao.mongodb.DocListReadModelRepository;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import io.ejangs.docsa.domain.doc.readmodel.util.DocListReadModelMapper;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;
import io.ejangs.docsa.domain.edge.util.GraphMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocQueryService {

    private final DocListReadModelRepository docListReadModelRepository;

    private final DocReader docReader;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final EdgeService edgeService;

    @Value("${cloud.aws.s3.public-base-url}")
    private String cdnUrl;

    @Transactional(readOnly = true)
    public Page<DocPageResponse> getPage(Long userId, Pageable pageable) {
        return getDocListReadModelPage(userId, pageable).map(
                model -> DocListReadModelMapper.toListResponse(model, cdnUrl));
    }

    @Transactional(readOnly = true)
    public Page<DocSimplePageResponse> getSimplePage(Long userId, Pageable pageable) {
        return getDocListReadModelPage(userId, pageable).map(
                DocListReadModelMapper::toListSimpleResponse);
    }

    @Transactional(readOnly = true)
    public Page<DocPageResponse> searchList(Long userId, String keyword, Pageable pageable) {
        return docListReadModelRepository
                .findByUserIdAndDeletedFalseAndTitleContainingIgnoreCase(userId, keyword, pageable)
                .map(model -> DocListReadModelMapper.toListResponse(model, cdnUrl));
    }

    private Page<DocListReadModel> getDocListReadModelPage(Long userId, Pageable pageable) {
        return docListReadModelRepository.findByUserIdAndDeletedFalse(userId, pageable);
    }

    // 문서 조회시 그래프를 그리기 위한 응답 생성
    @Transactional(readOnly = true)
    public GraphResponse getGraph(Long userId, Long documentId) {

        String docTitle = docReader.getByIdAndUserId(documentId, userId).getTitle();

        //  Branch, Commit, Edge 각각 별도 조회 (Projection 쿼리)
        List<BranchGraphDto> branches = branchQueryService.getBranchGraphList(documentId);
        if (branches.isEmpty()) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND);
        }
        List<CommitGraphDto> commits = commitQueryService.getCommitGraphList(documentId);
        List<EdgeDto> edges = edgeService.getEdgeDtoByDocId(documentId);

        return GraphMapper.toCommitGraphResponse(docTitle, commits, edges, branches);
    }

}
