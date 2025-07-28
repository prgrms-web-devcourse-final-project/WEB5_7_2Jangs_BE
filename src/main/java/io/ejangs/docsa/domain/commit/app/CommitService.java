package io.ejangs.docsa.domain.commit.app;

import com.mongodb.MongoException;
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
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.doc.util.EdgeMapper;
import io.ejangs.docsa.domain.save.app.SaveService;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoDeleteMapper;
import io.ejangs.docsa.global.mongo.deletion.util.MongoIdsCollector;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitService {

    private final CommitRepository commitRepository;
    private final CommitBlockSequenceRepository cbsRepository;

    private final DocService docService;
    private final BranchService branchService;
    private final BlockService blockService;
    private final SaveService saveService;
    private final EdgeService edgeService;

    private final CommitContentAssembler assembler;
    private final MongoIdsCollector mongoIdsCollector;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${default.branch}")
    private String defaultBranchName;

    @Transactional(rollbackFor = Exception.class)
    public CreateCommitResponse createCommit(Long docId,
            CreateCommitRequest commitRequest,
            Long userId) {

        branchService.checkBranchInDocOwnedByUser(docId, commitRequest.branchId(), userId);
        // * 1. documentId로 문서가 존재하는지 검사(JPA)
        Doc doc = docService.getById(docId);
        // * 2. commitRequest의 branchId로 브랜치가 존재하는지 검사(JPA)
        Branch branch = branchService.getById(commitRequest.branchId());

        // * 3. Commit Entity만들어서 DB에 저장
        Commit savedCommit = saveCommit(branch, commitRequest);
        branch.initializeRootCommitIfNull(savedCommit);

        // * 4. branchId를 기반으로 Save가 있다면 삭제
        String saveMongoId = saveService.deleteSaveIfExists(branch.getId());

        // * 5. 변경 전 Commit이 어떤 것인지 branch의 데이터를 통해 찾기(JPA - 지연로딩)
        Commit baseCommit = getBaseCommit(branch);
        branch.updateLeafCommit(savedCommit);

        // * 6. 새로운 간선 생성
        // 문서의 최초 커밋일 경우에는 간선을 새로 만들지 않는다
        if (baseCommit != null) {
            Edge newEdge = EdgeMapper.toEntity(doc, baseCommit, savedCommit);
            edgeService.saveEdge(newEdge);
        }

        List<Block> savedBlocks = null;
        CommitBlockSequence savedCbs = null;

        try {
            // * 7. 변경사항이 있는 block들을 DB에 저장(MongoDB)
            savedBlocks = blockService.saveBlocks(commitRequest.blocks());

            // * 8. baseCommit에서 사용한 block _id를 가져오기
            // * 9. block _id 로 이전 Commit에서 사용한 block 가져오기
            List<Block> baseCommitBlocks = getBaseCommitBlocks(baseCommit);

            // * 10. blockId는 editor.js에서 만들어주는 uniqueId
            List<String> newOrder = createBlockOrder(commitRequest.blockOrders(), savedBlocks,
                    baseCommitBlocks);
            // * 13. MongoDB에 cbs저장
            savedCbs = saveCommitBlockSequence(newOrder);
            // * 14. Commit에 MongoId 세팅
            savedCommit.initializeCommitMongoId(savedCbs.getId());
        } catch (Exception e) {
            rollbackMongoDb(savedBlocks, savedCbs);
            if (e instanceof MongoException) {
                throw new CustomException(CommitErrorCode.FAIL_SAVE_MONGODB);
            } else if (e instanceof CustomException) {
                throw (CustomException) e;
            }
            log.error("fail to save commit ", e);
            throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
        }

        RenewUpdatedAtHelper.touch(branch);
        MongoIdsDto commitDeleteMongoIds = MongoDeleteMapper
                .toMongoIdsDto(saveMongoId, null, null);

        eventPublisher.publishEvent(commitDeleteMongoIds);
        return CommitMapper.toCreateCommitResponse(savedCommit);
    }

    @Transactional(readOnly = true)
    public CommitResponse getCommit(Long docId, Long commitId, Long userId) {

        docService.checkDocByIdAndUserId(docId, userId);
        List<Map<String, Object>> assemble = getWholeContent(commitId);

        return CommitMapper.toCommitResponse(assemble);
    }

    @Transactional(readOnly = true)
    public CompareMergeCommitResponse compareCommitForMerge(Long docId, Long baseId, Long targetId,
            Long userId) {

        docService.checkDocByIdAndUserId(docId, userId);
        List<Map<String, Object>> baseContent = getWholeContent(baseId);
        List<Map<String, Object>> targetContent = getWholeContent(targetId);

        return CommitMapper.toCompareMergeCommitResponse(baseContent, targetContent);
    }

    @Transactional
    public CreateCommitResponse mergeCommit(Long docId, MergeCommitRequest mergeRequest,
            Long userId) {

        CommitMongoIdsDto commitMongoIds = null;
        try {
            // 문서가 존재하는지 검사
            // 브랜치가 존재하는지 검사
            Long baseBranchId = mergeRequest.baseBranchId();
            Long targetBranchId = mergeRequest.targetBranchId();

            checkBranch(baseBranchId, targetBranchId);
            branchService.checkBranchInDocOwnedByUser(docId, baseBranchId, userId);
            branchService.checkBranchInDocOwnedByUser(docId, targetBranchId, userId);

            Doc doc = docService.getById(docId);
            Branch baseBranch = branchService.getById(baseBranchId);
            Branch targetBranch = branchService.getById(targetBranchId);

            // Block과 Cbs를 저장
            commitMongoIds = saveBlockAndSequence(mergeRequest.content());

            // Commit을 저장
            MergeCommitDto mergeCommitDto = saveMergeCommit(doc, baseBranch, targetBranch,
                    mergeRequest, commitMongoIds.cbsId());

            MongoIdsDto commitDeleteMongoIds = MongoDeleteMapper
                    .toMongoIdsDto(mergeCommitDto.saveMongoIds(), null, null);

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
            throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCommit(Long docId, Long commitId, Long userId) {
        try {
            docService.checkDocByIdAndUserId(docId, userId);

            Commit commit = getById(commitId);
            Doc doc = docService.getById(docId);
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

            commitRepository.deleteById(commit.getId());
            eventPublisher.publishEvent(commitDeleteMongoIds);
        } catch (CustomException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            // DataIntegrityViolationException 예외처리 도입시 변경될 수 있음
            throw new CustomException(CommitErrorCode.FAIL_DELETE_COMMIT);
        }
    }

    private void checkFromOrRootCommit(Commit commit) {
        if (branchService.checkFromOrRootCommitInBranch(commit)) {
            throw new CustomException(CommitErrorCode.CAN_NOT_DELETE_COMMIT);
        }
    }

    private void checkLeafCommit(Commit commit) {
        if (!commit.getId().equals(commit.getBranch().getLeafCommit().getId())) {
            throw new CustomException(CommitErrorCode.CAN_NOT_DELETE_COMMIT);
        }
    }

    private void checkBranch(Long baseBranchId, Long targetBranchId) {
        if (baseBranchId == null || targetBranchId == null || baseBranchId < 0 || targetBranchId < 0
                || baseBranchId.equals(targetBranchId)) {
            throw new CustomException(CommitErrorCode.COMMIT_BAD_REQUEST);
        }
    }

    private MergeCommitDto saveMergeCommit(Doc doc, Branch baseBranch, Branch targetBranch,
            MergeCommitRequest request, String commitMongoId) {

        Commit commit = CommitMapper.toEntity(baseBranch, request);
        commit.initializeCommitMongoId(commitMongoId);
        Commit savedCommit = commitRepository.save(commit);
        commitRepository.flush();

        baseBranch.addCommit(savedCommit);

        Commit baseCommit = getLeafCommit(baseBranch);
        Commit targetCommit = getLeafCommit(targetBranch);

        Edge edge1 = EdgeMapper.toEntity(doc, baseCommit, savedCommit);
        Edge edge2 = EdgeMapper.toEntity(doc, targetCommit, savedCommit);
        edgeService.saveEdge(edge1);
        edgeService.saveEdge(edge2);

        baseBranch.updateLeafCommit(savedCommit);
        String saveMongoId = saveService.deleteSaveIfExists(baseBranch.getId());
        RenewUpdatedAtHelper.touch(baseBranch);

        branchService.saveBranch(baseBranch);

        return CommitMapper.toMergeCommitDto(savedCommit, saveMongoId);
    }

    private CommitMongoIdsDto saveBlockAndSequence(List<BlockDto> blocks) {
        List<Block> savedBlocks = null;
        try {
            savedBlocks = blockService.saveBlocks(blocks);
            List<String> blockSequence = savedBlocks.stream()
                    .map(Block::getId)
                    .toList();

            CommitBlockSequence cbs = CommitBlockSequenceMapper.toEntity(blockSequence);
            CommitBlockSequence savedCbs = cbsRepository.save(cbs);

            return new CommitMongoIdsDto(savedCbs.getId(), blockSequence);
        } catch (Exception e) {
            if (savedBlocks != null) {
                savedBlocks.forEach(block -> blockService.deleteBlock(block.getId()));
            }
            throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
        }
    }

    private void rollbackMongoTransaction(CommitMongoIdsDto commitMongoIds) {
        try {
            // 관련된 Block들도 삭제 (필요한 경우)
            commitMongoIds.blockIds().forEach(blockService::deleteBlock);

            // CommitBlockSequence 삭제
            cbsRepository.deleteById(commitMongoIds.cbsId());
        } catch (Exception e) {
            log.error("Failed to rollback MongoDB", e);
            // TODO 롤백 실패 로직 고민 필요
        }
    }

    private Commit getLeafCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = getById(commitId);
        return assembler.assemble(commit.getCommitMongoId());
    }

    public Commit getById(Long commitId) {
        return commitRepository.findById(commitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    private void rollbackMongoDb(List<Block> savedBlocks, CommitBlockSequence savedCbs) {
        try {
            // 저장된 블록들 삭제
            if (savedBlocks != null) {
                blockService.deleteAll(savedBlocks);
                log.info("Rolled back {} saved blocks", savedBlocks.size());
            }

            // 저장된 cbs 삭제
            if (savedCbs != null) {
                cbsRepository.delete(savedCbs);
            }
        } catch (Exception e) {
            log.error("Failed to rollback MongoDB", e);
            // TODO 롤백 실패 로직 고민 필요
        }
    }

    private Commit getBaseCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElse(branch.getFromCommit());
    }

    private List<Block> getBaseCommitBlocks(Commit baseCommit) {
        if (baseCommit == null) {
            return List.of();
        }
        CommitBlockSequence cbs = getCommitBlockSequence(baseCommit);
        return blockService.getAllById(cbs.getBlockOrders());
    }

    private CommitBlockSequence getCommitBlockSequence(Commit commit) {
        return cbsRepository.findById(commit.getCommitMongoId())
                .orElseThrow(
                        () -> new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND));
    }

    private List<String> createBlockOrder(List<String> requestedBlockOrders,
            List<Block> savedBlocks, List<Block> baseCommitBlocks) {
        List<String> newOrder = new ArrayList<>();

        for (String blockId : requestedBlockOrders) {
            Block block = getBlockById(blockId, savedBlocks, baseCommitBlocks);
            newOrder.add(block.getId());
        }

        return newOrder;
    }

    private Block getBlockById(String blockId, List<Block> savedBlocks,
            List<Block> baseCommitBlocks) {
        // * 11. blockOrder의 uniqueId가 새로 저장된 블록에 있는지 찾기
        Optional<Block> block = findBlockByIdInList(blockId, savedBlocks);

        // * 12. 없으면 이전 Commit의 블록에서 찾기
        if (block.isEmpty()) {
            block = findBlockByIdInList(blockId, baseCommitBlocks);
        }

        return block.orElseThrow(
                () -> new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_INVALID));
    }

    private Optional<Block> findBlockByIdInList(String blockId, List<Block> blocks) {
        return blocks.stream()
                .filter(block -> blockId.equals(block.getContent().get("id")))
                .findFirst();
    }

    private CommitBlockSequence saveCommitBlockSequence(List<String> newOrder) {
        CommitBlockSequence cbs = CommitBlockSequenceMapper.toEntity(newOrder);
        return cbsRepository.save(cbs);
    }

    private Commit saveCommit(Branch branch, CreateCommitRequest commitRequest) {
        Commit commit = CommitMapper.toEntity(branch, commitRequest);
        Commit savedCommit = commitRepository.save(commit);
        commitRepository.flush();
        branch.addCommit(savedCommit);
        return savedCommit;
    }
}
