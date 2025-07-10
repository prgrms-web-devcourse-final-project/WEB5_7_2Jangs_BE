package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.block.dao.BlockRepository;
import io.ejangs.docsa.domain.block.dto.response.BlockDto;
import io.ejangs.docsa.domain.block.entity.Block;
import io.ejangs.docsa.domain.block.util.BlockMapper;
import io.ejangs.docsa.domain.branch.dao.BranchRepository;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.entity.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.save.save.SaveRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BlockErrorCode;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitService {

    private final CommitRepository commitRepository;
    private final DocumentRepository documentRepository;
    private final BranchRepository branchRepository;
    private final BlockRepository blockRepository;
    private final SaveRepository saveRepository;

    @Transactional
    public CreateCommitResponse createCommit(Long documentId, Long userId,
            CreateCommitRequest commitRequest) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        Branch branch = branchRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));

        Commit entity = CommitMapper.toEntity(branch, commitRequest);
        // 변경사항있는 '블럭'을 DB에 저장
        saveBlocks(document, commitRequest.blocks());

        // 현재 '커밋'에서의 '블럭'의 순서를 CommitBlockSequence DB에 저장
        List<CommitBlockSequence> commitBlockSequences = makeCommitBlockSequence(entity,
                commitRequest.blockOrders());
        entity.updateCommitBlocks(commitBlockSequences);

        Commit save = commitRepository.save(entity);
        // 현재 '브랜치'에서 작성중인 '저장'을 삭제
        saveRepository.findByBranchId(branch.getId())
                .ifPresent(saveRepository::delete);

        return CommitMapper.toCreateCommitResponse(save);
    }

    private void saveBlocks(Document document, List<BlockDto> blocks) {
        for (BlockDto block : blocks) {
            Block entity = BlockMapper.toEntity(document, block);
            blockRepository.save(entity);
        }
    }

    private List<CommitBlockSequence> makeCommitBlockSequence(Commit commit,
            List<String> blockOrders) {
        ArrayList<CommitBlockSequence> commitBlockSequences = new ArrayList<>();
        ArrayList<Block> blocks = new ArrayList<>();

        for (String blockUniqueId : blockOrders) {
            Block block = blockRepository.findLatestByUniqueId(blockUniqueId)
                    .orElseThrow(() -> new CustomException(BlockErrorCode.BLOCK_NOT_FOUND));
            blocks.add(block);
        }

        for (int i = 0; i < blocks.size(); i++) {
            CommitBlockSequence.builder()
                    .first(i == 0)
                    .commit(commit)
                    .currentBlock(blocks.get(i))
                    .nextBlock(i + 1 < blocks.size() ? blocks.get(i + 1) : null)
                    .build();
        }

        return commitBlockSequences;
    }
}
