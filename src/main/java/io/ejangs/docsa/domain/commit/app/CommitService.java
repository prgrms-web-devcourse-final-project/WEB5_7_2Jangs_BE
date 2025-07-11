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
import io.ejangs.docsa.domain.commit.util.CommitBlockSequenceFactory;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.save.dao.SaveRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
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

    private final CommitBlockSequenceFactory commitBlockSequenceFactory;

    @Transactional
    public CreateCommitResponse createCommit(Long documentId, Long userId,
            CreateCommitRequest commitRequest) {

        if (commitRequest.blocks().isEmpty()) {
            throw new CustomException(CommitErrorCode.COMMIT_BAD_REQUEST);
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        Branch branch = branchRepository.findById(commitRequest.branchId())
                .orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));

        Commit entity = CommitMapper.toEntity(branch, commitRequest);
        // 변경사항있는 '블럭'을 DB에 저장

        List<String> updatedBlocks = saveBlocks(document, commitRequest.blocks());

        // 현재 '커밋'에서의 '블럭'의 순서를 CommitBlockSequence DB에 저장
        List<CommitBlockSequence> commitBlockSequences = commitBlockSequenceFactory.create(entity,
                commitRequest.blockOrders(), updatedBlocks, branch);
        entity.initializeCommitBlocks(commitBlockSequences);

        Commit save = commitRepository.save(entity);
        // 현재 '브랜치'에서 작성중인 '저장'을 삭제
        saveRepository.findByBranchId(branch.getId()).ifPresent(saveRepository::delete);
        // '브랜치'의 leafCommit을 현재 작성한 '커밋'으로 변경
        branch.updateLeafCommit(save);

        return CommitMapper.toCreateCommitResponse(save);
    }

    private List<String> saveBlocks(Document document, List<BlockDto> blocks) {
        List<String> updatedBlock = new ArrayList<>();
        for (BlockDto block : blocks) {
            Block entity = BlockMapper.toEntity(document, block);
            blockRepository.save(entity);
            updatedBlock.add(entity.getUniqueId());
        }
        return updatedBlock;
    }
}
