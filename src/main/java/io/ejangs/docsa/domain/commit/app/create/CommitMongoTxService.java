package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.block.app.BlockService;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.util.CommitBlockSequenceMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockSequenceErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
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
public class CommitMongoTxService {

    private final BlockService blockService;
    private final CommitBlockSequenceRepository cbsRepository;

    @Transactional(transactionManager = "mongoTransactionManager")
    public MongoIdsDto createMongoPart(CreateCommitRequest request, String baseCommitCbsMongoId) {
        // 이번 커밋에서 "새로 저장된" 블록들 (Mongo insert)
        List<Block> newBlocks = blockService.saveBlocks(request.blocks());

        // 이전 커밋(CBS)에 포함된 블록들 조회
        List<Block> baseCommitBlocks = getBaseCommitBlocks(baseCommitCbsMongoId);

        // 요청된 editor.js block order(editorId 배열)를 실제 Mongo _id 배열로 변환
        List<String> orderedBlockMongoIds =
                resolveOrderedBlockMongoIds(request.blockOrders(), newBlocks, baseCommitBlocks);

        CommitBlockSequence newCbs = saveCommitBlockSequence(orderedBlockMongoIds);

        List<String> createdBlockMongoIds = newBlocks.stream()
                .map(Block::getId)
                .toList();

        // MySQL실패시 보상 삭제 대상 return
        return new MongoIdsDto(null, List.of(newCbs.getId()), createdBlockMongoIds);
    }

    private List<Block> getBaseCommitBlocks(String baseCommitCbsMongoId) {
        if (baseCommitCbsMongoId == null) {
            return List.of();
        }

        CommitBlockSequence baseCbs = cbsRepository.findById(baseCommitCbsMongoId)
                .orElseThrow(() -> new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_NOT_FOUND));

        // baseCbs.getBlockOrders() == 이전 커밋이 참조하던 "Block의 Mongo _id 리스트"
        return blockService.getAllById(baseCbs.getBlockOrders());
    }

    //TODO
    // 현재 resolveOrderedBlockMongoIds는 요청 블록 수 * (new + base 블록 수) 만큼 선형 탐색을 수행하는 O(N*M) 구조.
    // 문서가 커질 경우 성능 병목 가능성 있음.
    // editorId -> Block(or mongoId) 매핑 Map을 미리 구성하여 O(N)으로 리팩토링 필요.
    private List<String> resolveOrderedBlockMongoIds(
            List<String> requestedEditorBlockIds,
            List<Block> newBlocks,
            List<Block> baseCommitBlocks
    ) {
        List<String> orderedBlockMongoIds = new ArrayList<>();

        for (String editorBlockId : requestedEditorBlockIds) {
            Block block = findBlockByEditorIdOrThrow(editorBlockId, newBlocks, baseCommitBlocks);
            orderedBlockMongoIds.add(block.getId()); // Mongo _id
        }

        return orderedBlockMongoIds;
    }

    private Block findBlockByEditorIdOrThrow(
            String editorBlockId,
            List<Block> newBlocks,
            List<Block> baseCommitBlocks
    ) {
        // 이번 커밋에서 새로 생성된 블록인지 확인
        Optional<Block> found = findByEditorId(editorBlockId, newBlocks);

        // 없다면 이전 커밋에서 그대로 유지된 블록인지 확인
        if (found.isEmpty()) {
            found = findByEditorId(editorBlockId, baseCommitBlocks);
        }

        return found.orElseThrow(() -> new CustomException(BlockSequenceErrorCode.BLOCK_SEQUENCE_INVALID));
    }

    private Optional<Block> findByEditorId(String editorBlockId, List<Block> blocks) {
        // editor.js block id는 Mongo _id가 아니라 block.content.id에 들어있음
        return blocks.stream()
                .filter(b -> editorBlockId.equals(b.getContent().get("id")))
                .findFirst();
    }

    private CommitBlockSequence saveCommitBlockSequence(List<String> orderedBlockMongoIds) {
        CommitBlockSequence cbs = CommitBlockSequenceMapper.toDocument(orderedBlockMongoIds);
        return cbsRepository.save(cbs);
    }


}
