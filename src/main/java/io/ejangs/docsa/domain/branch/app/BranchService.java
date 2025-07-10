package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.document.entity.Document;
import io.ejangs.docsa.domain.save.dao.SaveRepository;
import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.commit.dao.CommitRepository;
import io.ejangs.docsa.domain.branch.dao.BranchRepository;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final DocumentRepository documentRepository;
    private final CommitRepository commitRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final CommitContentAssembler commitContentAssembler;

    @Transactional
    public BranchCreateResponse createBranch(Long documentId,
            BranchCreateRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        Long fromId = request.fromCommitId();
        if (fromId != null) {
            Commit fromCommit = commitRepository.findById(fromId)
                    .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

            if (!fromCommit.getBranch().getDocument().getId().equals(documentId)) {
                throw new CustomException(DocumentErrorCode.COMMIT_NOT_IN_DOCUMENT);
            }

            // fromCommit 브랜치의 leaf가 본인이면 최신 커밋
            boolean isLeaf = fromCommit.getBranch().getLeafCommit() != null
                    && fromCommit.getBranch().getLeafCommit().getId().equals(fromId);

            // 최신커밋이면 있던 브랜치에 저장 만들기, 아니면 새로운 브랜치에 저장 만들기
            if (isLeaf) return createSaveAndResponse(fromCommit.getBranch(),commitContentAssembler.assemble(fromCommit));
            else {
                Branch newBranch = branchRepository.save(BranchMapper.toEntity(request, document, fromCommit));
                return createSaveAndResponse(newBranch,commitContentAssembler.assemble(fromCommit));
            }
        }
        // fromId가 null이면 최초 새 브랜치 생성 + 저장 만들기
        Branch newBranch = branchRepository.save(BranchMapper.toEntity(request, document, null));
        return createSaveAndResponse(newBranch, "");
    }

    private BranchCreateResponse createSaveAndResponse(Branch branch, String content) {
        Save save = Save.builder()
                .content(content)
                .branch(branch)
                .build();
        save = saveRepository.save(save);
        return BranchMapper.toBranchCreateResponse(branch, save);
    }
}


