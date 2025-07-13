package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class BranchService {

    private final DocumentRepository documentRepository;
    private final CommitRepository commitRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;
    private final CommitContentAssembler commitContentAssembler;


    @Transactional
    public BranchCreateResponse createBranchOrSave(Long documentId, BranchCreateRequest request) {

        if (!documentRepository.existsById(documentId)) {
            throw (new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        }

        Long fromCommitId = request.fromCommitId();

        // 이어서 작업하기는 최초 브랜치가 아닌 경우(fromCommitId 존재)만 담당
        if (fromCommitId != null) {
            Commit fromCommit = commitRepository.findById(fromCommitId)
                    .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

            Branch fromBranch = fromCommit.getBranch();

            if (!fromBranch.getDoc().getId().equals(documentId)) {
                throw new CustomException(DocumentErrorCode.COMMIT_NOT_IN_DOCUMENT);
            }
            // fromCommit 이 곧 feomBranch의 leafCommit이면 최신 커밋일 때
            boolean isLeaf =
                    fromBranch.getLeafCommit() != null && fromBranch.getLeafCommit().getId()
                            .equals(fromCommitId);

            // 기존 브랜치에 새로운 저장 반들기
            if (isLeaf) {
                Save save = createSave(fromBranch, fromCommit.getCommitMongoId());
                return BranchMapper.toBranchCreateResponse(fromBranch, save);
            }
            // fromCommit이 중간 커밋이면 새로운 브랜치 생성, 새로운 저장 생성, 새로운 브랜치의 leafCommit은 null
            else {
                Branch newBranch = Branch.builder().name(request.name()).doc(fromBranch.getDoc())
                        .fromCommit(fromCommit).build();

                branchRepository.save(newBranch);

                Save save = createSave(fromBranch, fromCommit.getCommitMongoId());
                return BranchMapper.toBranchCreateResponse(newBranch, save);
            }
        }

        // 최초의 브랜치 생성 이외에는 request.fromCommitId != null
        throw new CustomException(CommitErrorCode.INVALID_FROM_COMMIT);


    }

    // 새로운 저장 만들기 유틸 메서드
    private Save createSave(Branch branch, String commitMongoId) {

        // MongoDB에저장할 커밋의 본문 조립 Map 변환
        List<Map<String, Object>> blockContents = commitContentAssembler.assemble(commitMongoId);
        Map<String, Object> content = new HashMap<>();
        content.put("blocks", blockContents);

        //MongoDB SAveContent 저장
        SaveContent saveContent = SaveContent.builder().content(content).build();

        SaveContent saved = saveContentRepository.save(saveContent);

        //RDB Save 저장
        Save save = Save.builder().branch(branch).saveMongoId(saved.getId()).build();

        return saveRepository.save(save);

    }
}


