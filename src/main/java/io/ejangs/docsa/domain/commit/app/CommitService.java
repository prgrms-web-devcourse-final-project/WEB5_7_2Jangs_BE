package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
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
import io.ejangs.docsa.domain.doc.app.DocumentService;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
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

    private final DocumentService docService;
    private final BranchService branchService;
    private final BlockService blockService;
    private final BlockRepository blockRepository;

    private final SaveContentRepository saveContentRepository;
    private final SaveRepository saveRepository;

    @Transactional
    public CreateCommitResponse createCommit(Long docId, Long userId,
            CreateCommitRequest commitRequest) {

        /**
         * 1. documentId로 문서가 존재하는지 검사(JPA)
         * 2. commitRequest의 branchId로 브랜치가 존재하는지 검사(JPA)
         * 3. 변경사항이 있는 block들을 DB에 저장(MongoDB)
         * 4. 변경 전 Commit이 어떤 것인지 branch의 데이터를 통해 찾기(JPA - 지연로딩)
         * 5. baseCommit에서 사용한 block _id를 가져오기
         * 6. block _id 로 이전 Commit에서 사용한 block 가져오기
         * 7. blockId는 editor.js에서 만들어주는 uniqueId
         * 8. blockOrder의 uniqueId가 변경된 블럭 중에 있는지 찾아보기
         * 9. 없으면 이전 Commit에서 사용한 블럭에서 찾기
         * 10. MongoDB에 cbs저장
         * 11. Commit Entity만들어서 DB에 저장
         * 12. branchId를 기반으로 Save가 있다면 삭제
         */

        // * 1. documentId로 문서가 존재하는지 검사(JPA)
        docService.notFoundDocCheck(docId);
        // * 2. commitRequest의 branchId로 브랜치가 존재하는지 검사(JPA)
        Branch branch = branchService.findById(docId);

        List<Block> savedBlocks = null;
        CommitBlockSequence savedCbs = null;

        try {
            // * 3. 변경사항이 있는 block들을 DB에 저장(MongoDB)
            savedBlocks = blockService.saveBlocks(commitRequest.blocks());
            // * 4. 변경 전 Commit이 어떤 것인지 branch의 데이터를 통해 찾기(JPA - 지연로딩)
            Commit baseCommit = getBaseCommit(branch);
            List<Block> baseCommitBlocks = getBaseCommitBlocks(baseCommit);

            // * 7. blockId는 editor.js에서 만들어주는 uniqueId
            List<String> newOrder = createBlockOrder(commitRequest.blockOrders(), savedBlocks,
                    baseCommitBlocks);

            // * 10. MongoDB에 cbs저장
            savedCbs = saveCommitBlockSequence(newOrder);

            // * 11. Commit Entity만들어서 DB에 저장
            Commit savedCommit = saveCommit(branch, commitRequest, savedCbs.getId());

            // * 12. branchId를 기반으로 Save가 있다면 삭제
            deleteSaveIfExists(branch.getId());

            return CommitMapper.toCreateCommitResponse(savedCommit);
        } catch (Exception e) {
            rollbackMongoDb(savedBlocks, savedCbs);
            throw e;
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
            // 롤백 실패 어쩌지...
        }
    }

    private Commit getBaseCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElse(branch.getFromCommit());
    }

    // blockService로 이동 예정
    private List<Block> getBaseCommitBlocks(Commit baseCommit) {
        if (baseCommit == null) {
            return List.of();
        }
        // * 5. baseCommit에서 사용한 block _id를 가져오기
        CommitBlockSequence cbs = getCommitBlockSequence(baseCommit);
        // * 6. block _id 로 이전 Commit에서 사용한 block 가져오기
        return blockRepository.findAllById(cbs.getBlockOrders());
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
        // * 8. blockOrder의 uniqueId가 새로 저장된 블록에 있는지 찾기
        Optional<Block> block = findBlockByIdInList(blockId, savedBlocks);

        // * 9. 없으면 이전 Commit의 블록에서 찾기
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

    private Commit saveCommit(Branch branch, CreateCommitRequest commitRequest, String cbsId) {
        Commit commit = CommitMapper.toEntity(branch, commitRequest, cbsId);
        return commitRepository.save(commit);
    }

    // saveService로 이동 예정
    private void deleteSaveIfExists(Long branchId) {
        saveRepository.findByBranchId(branchId).ifPresent(save -> {
            saveContentRepository.findById(save.getSaveMongoId())
                    .ifPresent(saveContentRepository::delete);
            saveRepository.delete(save);
        });
    }
}
