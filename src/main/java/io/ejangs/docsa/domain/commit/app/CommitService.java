package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.dto.CommitMongoIdsDto;
import io.ejangs.docsa.domain.commit.dto.MergeCommitDto;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitBlockSequenceMapper;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.EdgeMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitService {

    private final DocQueryService docQueryService;
    private final CommitQueryService commitQueryService;
    private final BranchService branchService;
    private final CommitCreateOrchestrator commitCreateOrchestrator;
    private final BlockService blockService;
    private final SaveService saveService;
    private final EdgeService edgeService;

    private final CommitContentAssembler assembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final ApplicationEventPublisher eventPublisher;

    public CreateCommitResponse createCommit(Long docId,
            CreateCommitRequest request,
            Long userId) {

        branchService.checkBranchInDocOwnedByUser(docId, request.branchId(), userId);

        Doc doc = docQueryService.getById(docId);
        Branch branch = branchService.getById(request.branchId());

        Long baseCommitId = Optional.ofNullable(branch.getLeafCommit())
                .map(Commit::getId)
                .orElseGet(() -> Optional.ofNullable(branch.getFromCommit())
                        .map(Commit::getId)
                        .orElse(null));
        String baseCommitCbsMongoId = (baseCommitId == null) ? null
                : commitQueryService.findCommitMongoIdById(baseCommitId).orElse(null);

        Commit newCommit = commitCreateOrchestrator.create(request, baseCommitCbsMongoId, doc, branch);

        return CommitMapper.toCreateCommitResponse(newCommit);
    }

    public CommitResponse getCommit(Long docId, Long commitId, Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> content = getWholeContent(commitId);
        return CommitMapper.toCommitResponse(content);
    }

    @Transactional(readOnly = true)
    public CompareMergeCommitResponse compareCommitForMerge(Long docId, Long baseId, Long targetId,
            Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> baseContent = getWholeContent(baseId);
        List<Map<String, Object>> targetContent = getWholeContent(targetId);
        return CommitMapper.toCompareMergeCommitResponse(baseContent, targetContent);
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = getById(commitId);
        return assembler.assemble(commit.getCommitMongoId());
    }

    @Transactional
    public CreateCommitResponse mergeCommit(Long docId, MergeCommitRequest mergeRequest,
            Long userId) {

        CommitMongoIdsDto commitMongoIds = null;
        try {
            // 문서가 존재하는지 검사
            // 브랜치가 존재하는지 검사
            Long baseCommitId = mergeRequest.baseCommitId();
            Long targetCommitId = mergeRequest.targetCommitId();

            Commit baseCommit = getById(baseCommitId);
            Commit targetCommit = getById(targetCommitId);
            checkLeafCommit(baseCommit);
            checkLeafCommit(targetCommit);

            Branch baseBranch = baseCommit.getBranch();
            Branch targetBranch = targetCommit.getBranch();

            Long baseBranchId = baseBranch.getId();
            Long targetBranchId = targetBranch.getId();

            checkBranch(baseBranchId, targetBranchId);
            branchService.checkBranchInDocOwnedByUser(docId, baseBranchId, userId);
            branchService.checkBranchInDocOwnedByUser(docId, targetBranchId, userId);

            Doc doc = docQueryService.getById(docId);

            // Block과 Cbs를 저장
            commitMongoIds = saveBlockAndSequence(mergeRequest.content());

            // Commit을 저장
            MergeCommitDto mergeCommitDto = saveMergeCommit(doc, baseBranch, targetBranch,
                    mergeRequest, commitMongoIds.cbsId());

            List<String> saveIds = mergeCommitDto.saveMongoIds() == null
                    ? List.of()
                    : List.of(mergeCommitDto.saveMongoIds());
            MongoIdsDto commitDeleteMongoIds = new MongoIdsDto(saveIds, null, null);

            log.warn("[MONGO] mergeCommit");
            eventPublisher.publishEvent(commitDeleteMongoIds);
            return CommitMapper.toCreateCommitResponse(mergeCommitDto.commit());
        } catch (Exception e) {
            if (commitMongoIds != null) {
                rollbackMongoTransaction(commitMongoIds);
            }
            if (e instanceof CustomException) {
                throw (CustomException) e;
            }
            log.error("Create Commit 알 수 없는 오류 - {}", e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.DATABASE_ERROR);
        }
    }

    private CommitMongoIdsDto saveBlockAndSequence(List<BlockDto> blocks) {
        List<Block> savedBlocks = null;
        try {
            savedBlocks = blockService.saveBlocks(blocks);
            List<String> blockSequence = savedBlocks.stream()
                    .map(Block::getId)
                    .toList();

            CommitBlockSequence cbs = CommitBlockSequenceMapper.toEntity(blockSequence);
            CommitBlockSequence savedCbs = commitQueryService.saveCommitBlockSequence(cbs);

            return new CommitMongoIdsDto(savedCbs.getId(), blockSequence);
        } catch (Exception e) {
            if (savedBlocks != null) {
                savedBlocks.forEach(block -> blockService.deleteBlock(block.getId()));
            }
            throw new CustomException(DatabaseErrorCode.DATABASE_ERROR);
        }
    }

    private MergeCommitDto saveMergeCommit(Doc doc, Branch baseBranch, Branch targetBranch,
            MergeCommitRequest request, String commitMongoId) {

        Commit commit = CommitMapper.toEntity(targetBranch, request);
        commit.initializeCommitMongoId(commitMongoId);
        Commit savedCommit = commitQueryService.saveAndFlush(commit);

        targetBranch.addCommit(savedCommit);

        Commit baseCommit = getLeafCommit(baseBranch);
        Commit targetCommit = getLeafCommit(targetBranch);

        Edge edge1 = EdgeMapper.toEntity(doc, baseCommit, savedCommit);
        Edge edge2 = EdgeMapper.toEntity(doc, targetCommit, savedCommit);
        edgeService.saveEdge(edge1);
        edgeService.saveEdge(edge2);

        targetBranch.updateLeafCommit(savedCommit);
        String saveMongoId = saveService.deleteSaveIfExists(targetBranch);
        RenewUpdatedAtHelper.touch(targetBranch);

        branchService.saveBranch(targetBranch);

        return CommitMapper.toMergeCommitDto(savedCommit, saveMongoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCommit(Long docId, Long commitId, Long userId) {
        Doc doc = docQueryService.getByIdAndUserId(docId, userId);

        Commit commit = getById(commitId);
        // LeafCommit일 경우에만 삭제 가능
        checkLeafCommit(commit);
        // 어느 브랜치의 FromCommit이나 RootCommit일 경우 삭제 불가능
        checkFromOrRootCommit(commit);

        // 간선을 삭제하면서 새로 LeafCommit이 될 Commit들을 수집
        List<Commit> prevCommits = edgeService.cutEdge(doc, commitId);

        for (Commit prevCommit : prevCommits) {
            Branch branch = prevCommit.getBranch();
            branch.updateLeafCommit(prevCommit);
            branch.removeCommit(commit);
            RenewUpdatedAtHelper.touch(branch);
        }

        MongoIdsDto commitDeleteMongoIds = mongoIdsCollector.collectFrom(prevCommits, commit);

        commitQueryService.deleteById(commit.getId());

        log.warn("[MONGO] deleteCommit");
        eventPublisher.publishEvent(commitDeleteMongoIds);
    }

    private void rollbackMongoTransaction(CommitMongoIdsDto commitMongoIds) {
        try {
            // 관련된 Block들도 삭제 (필요한 경우)
            commitMongoIds.blockIds().forEach(blockService::deleteBlock);

            // CommitBlockSequence 삭제
            commitQueryService.deleteCbsById(commitMongoIds.cbsId());
        } catch (Exception e) {
            log.error("Failed to rollback MongoDB", e);
            // TODO 롤백 실패 로직 고민 필요
        }
    }



    private Commit getById(Long commitId) {
        return commitQueryService.getById(commitId);
    }

    private Commit getLeafCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    private void checkFromOrRootCommit(Commit commit) {
        if (branchService.checkFromOrRootCommitInBranch(commit)) {
            throw new CustomException(CommitErrorCode.CAN_NOT_DELETE_COMMIT);
        }
    }

    private void checkBranch(Long baseBranchId, Long targetBranchId) {
        if (baseBranchId.equals(targetBranchId)) {
            throw new CustomException(CommitErrorCode.COMMIT_BAD_REQUEST);
        }
    }

    public void checkLeafCommit(Commit commit) {
        if (!commit.getId().equals(commit.getBranch().getLeafCommit().getId())) {
            throw new CustomException(CommitErrorCode.IS_NOT_LEAF_COMMIT);
        }
    }
}
