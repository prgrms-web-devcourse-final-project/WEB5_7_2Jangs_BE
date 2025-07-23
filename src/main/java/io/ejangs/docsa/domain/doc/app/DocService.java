package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dto.EdgeDto;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.util.DocListAssembler;
import io.ejangs.docsa.domain.doc.util.DocMapper;
import io.ejangs.docsa.domain.doc.util.GraphMapper;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocRepository docRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;
    private final DocListAssembler docListAssembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional(rollbackFor = Exception.class)
    public DocCreateResponse create(DocTitleRequest request, Long userId) {

        User user = getUserOrThrow(userId);

        String title = request.title();
        checkTitleDuplicate(userId, title);

        Doc doc = createDoc(user, title);
        Branch defaultBranch = createDefaultBranch(doc);

        Save defaultSave = Save.builder().branch(defaultBranch).build();

        //Mongo 저장을 RDB 저장 이 후에 진행하여 실패시 예외 발생으로 인한 종료
        //Mongo 저장실패 이종간 트랜잭션 고도화 필요
        SaveContent defaultSaveContent = createDefaultSaveContent();

        defaultSave.updateSaveMongoId(defaultSaveContent.getId());
        Save save = saveRepository.save(defaultSave);
        return DocMapper.toCreateResponse(doc, save);
    }

    private Doc createDoc(User user, String title) {
        Doc doc = docRepository.save(Doc.builder().title(title).user(user).build());
        docRepository.flush();
        user.addDocument(doc);
        return doc;
    }

    private Branch createDefaultBranch(Doc doc) {
        Branch branch =
                branchRepository.save(Branch.builder().name(defaultBranchName).doc(doc).build());
        doc.addBranch(branch);
        RenewUpdatedAtHelper.touch(branch);
        return branch;
    }

    private SaveContent createDefaultSaveContent() {
        try {
            return saveContentRepository.save(SaveContent.builder().build());
        } catch (DataAccessException e) {
            log.error("DefaultSaveContent Mongo 저장 실패 - {}", e.getMessage(), e);
            throw new CustomException(DocErrorCode.FAIL_CREATE_DOCUMENT);
        } catch (Exception e) {
            log.error("DefaultSaveContent Mongo 알 수 없는 오류 - {}", e.getMessage(), e);
            throw new CustomException(DocErrorCode.FAIL_CREATE_DOCUMENT);
        }
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
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<Doc> docList = docRepository.searchDocByTitle_FULLTEXT(keyword, userId, limit, offset);
        long totalCount = docRepository.countDocByTitle_FULLTEXT(keyword, userId);

        Page<Doc> docPages = new PageImpl<>(docList, pageable, totalCount);
        return docListAssembler.assembleDocList(docPages);
    }

    @Transactional
    public DocTitleUpdateResponse updateTitle(Long userId, Long docId, DocTitleRequest request) {
        String title = request.title();

        Doc doc = getDocByIdAndUserId(docId, userId);

        if (title.equals(doc.getTitle())) {
            throw new CustomException(DocErrorCode.SAME_AS_CURRENT_TITLE);
        }

        checkTitleDuplicate(userId, title);
        doc.updateTitle(title);

        return DocMapper.toUpdateResponse(doc);
    }

    private void checkTitleDuplicate(Long userId, String title) {
        Boolean alreadyExistsTitle = docRepository.existsByUserIdAndTitle(userId, title);
        if (alreadyExistsTitle) {
            throw new CustomException(DocErrorCode.TITLE_DUPLICATION);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    public Doc getDocByIdAndUserId(Long documentId, Long userId) {
        return docRepository.getDocByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));
    }

    public void checkDocByIdAndUserId(Long documentId, Long userId) {
        if (!docRepository.existsByIdAndUserId(documentId, userId)) {
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

        Doc doc = docRepository.findByIdWithBranchesAndEdges(documentId)
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));

        List<CommitDto> commits = doc.getBranches().stream().flatMap(b -> b.getCommits().stream())
                .map(GraphMapper::toCommitDto).toList();

        List<EdgeDto> edges = doc.getEdges().stream().map(GraphMapper::toEdgeDto).toList();

        List<BranchDto> branches =
                doc.getBranches().stream().map(GraphMapper::toBranchDto).toList();

        return GraphMapper.toCommitGraphResponse(doc.getTitle(), commits, edges, branches);
    }

    @Transactional
    public void delete(Long docId, Long userId) {
        User user = getUserOrThrow(userId);
        Doc doc = getDocByIdAndUserId(docId, userId);

        List<Branch> branches = doc.getBranches();

        MongoIdsDto docDeleteMongoIds = mongoIdsCollector.collectFrom(branches);

        user.removeDocument(doc);
        eventPublisher.publishEvent(docDeleteMongoIds);
    }

    @Transactional(readOnly = true)
    public void notFoundDocCheck(Long id) {
        if (!docRepository.existsById(id)) {
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
        }
    }
}
