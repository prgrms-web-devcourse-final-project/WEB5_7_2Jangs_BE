package io.ejangs.docsa.domain.commit.app;

import com.mongodb.MongoException;
import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
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
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(rollbackFor = Exception.class)
    public CreateCommitResponse createCommit(Long docId, CreateCommitRequest commitRequest) {

        try {
            // * 1. documentId로 문서가 존재하는지 검사(JPA)
            Doc doc = docService.getById(docId);
            // * 2. commitRequest의 branchId로 브랜치가 존재하는지 검사(JPA)
            Branch branch = branchService.findById(commitRequest.branchId());

            // * 3. Commit Entity만들어서 DB에 저장
            Commit savedCommit = saveCommit(branch, commitRequest);
            branch.updateLeafCommit(savedCommit);
            branch.initializeRootCommitIfNull(savedCommit);

            // * 4. branchId를 기반으로 Save가 있다면 삭제
            saveService.deleteSaveIfExists(branch.getId());

            // * 5. 변경 전 Commit이 어떤 것인지 branch의 데이터를 통해 찾기(JPA - 지연로딩)
            Commit baseCommit = getBaseCommit(branch);

            // * 6. 새로운 간선 생성
            Edge newEdge = EdgeMapper.toEntity(doc, baseCommit, savedCommit);
            edgeService.saveEdge(newEdge);

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
                }
                throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
            }

            RenewUpdatedAtHelper.touch(branch);
            return CommitMapper.toCreateCommitResponse(savedCommit);
        } catch (CustomException e) {
            log.error("Create Commit 저장 실패 - {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Create Commit 알 수 없는 오류 - {}", e.getMessage(), e);
            throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
        }

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
        return blockService.findAllById(cbs.getBlockOrders());
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
            Block block = findBlockById(blockId, savedBlocks, baseCommitBlocks);
            newOrder.add(block.getId());
        }

        return newOrder;
    }

    private Block findBlockById(String blockId, List<Block> savedBlocks,
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
