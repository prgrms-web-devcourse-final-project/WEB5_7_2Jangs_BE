package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.block.document.Block;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
//TODO: CBS 조회 책임 분리 검토(block부분은 blockService로 이동)
public class CommitContentAssembler {

    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;

    public List<Map<String, Object>> assemble(String commitMongoId) {
        CommitBlockSequence blockSeq = commitBlockSequenceRepository.findById(commitMongoId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

        List<String> blockIds = blockSeq.getBlockOrders();

        // findAllByID 의 결과 탐색시 N^2 -> N 연산 위해  Map으로 먼저 변환
        Map<String, Block> blockMap = blockRepository.findAllById(blockIds).stream()
                .collect(Collectors.toMap(Block::getId, Function.identity()));

        return blockIds.stream()
                .map(id -> Optional.ofNullable(blockMap.get(id))
                        .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND)))
                .map(Block::getContent)
                .toList();
    }
}
