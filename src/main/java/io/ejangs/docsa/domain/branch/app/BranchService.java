package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final CommitRepository commitRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;
    private final CommitContentAssembler commitContentAssembler;

    /**
     * '이어서 작업하기' 로직으로, 브랜치를 생성하고 저장을 추가하거나 기존 브랜치에 저장을 추가합니다.
     *
     * fromCommitId가 존재하면 기존 커밋에서 분기(branch)를 만들거나 저장(save)을 추가하는 상황입니다.
     * fromCommitId가 null이면 최초 브랜치 생성으로, 이 경우는 doc 도메인에서 처리합니다.
     */

    @Transactional
    public BranchCreateResponse createBranchOrSave(Long documentId, BranchCreateRequest request) {

        Long fromCommitId = request.fromCommitId();

        // fromCommitId가 null이면 최초 브랜치 생성 시도 -> 여기서는 허용하지 않음
        if (fromCommitId != null) {
            throw new CustomException(CommitErrorCode.INVALID_FROM_COMMIT);
        }

        // 분기 기준이 되는 커밋을 조회
        Commit fromCommit = commitRepository.findById(fromCommitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

        Branch fromBranch = fromCommit.getBranch();

        // 커밋이 요청한 문서에 속해 있는지 검증
        if (!fromBranch.getDoc().getId().equals(documentId)) {
            throw new CustomException(DocumentErrorCode.COMMIT_NOT_IN_DOCUMENT);
        }

        // fromCommit이 브랜치의 최신 커밋(leaf)인지 여부 판단
        boolean isLeaf = fromBranch.getLeafCommit() != null && fromBranch.getLeafCommit().getId()
                .equals(fromCommitId);

        if (isLeaf) {
            // 커밋이 브랜치의 최신 커밋인 경우 → 기존 브랜치에 새로운 저장(save)만 추가
            Save save = createSave(fromBranch, fromCommit.getCommitMongoId());
            return BranchMapper.toBranchCreateResponse(fromBranch, save);
        }
        else {
            // 중간 커밋에서 작업을 이어가는 경우 → 새로운 브랜치를 생성하고 저장도 함께 생성
            Branch newBranch = Branch.builder().name(request.name()).doc(fromBranch.getDoc())
                    .fromCommit(fromCommit).build();

            branchRepository.save(newBranch);

            Save save = createSave(newBranch, fromCommit.getCommitMongoId());
            return BranchMapper.toBranchCreateResponse(newBranch, save);
        }
    }

    /**
     * 커밋 내용을 기반으로 새로운 저장(save)을 생성합니다.
     * MongoDB와 RDB에 모두 저장합니다.
     */
    private Save createSave(Branch branch, String commitMongoId) {
        // 커밋의 블록 내용을 조립
        List<Map<String, Object>> blockContents = commitContentAssembler.assemble(commitMongoId);

        // MongoDB에 저장 후 mongoId 획득
        String mongoId = saveContentToMongo(blockContents);

        // RDB에 저장 (실패 시 Mongo 롤백 포함)
        return saveToRDB(branch, mongoId);

    }

    /**
     * 블록 내용을 MongoDB에 저장합니다.
     *
     * 저장 구조는 { "blocks": [...] } 형식의 문서입니다.
     */
    private String saveContentToMongo(List<Map<String, Object>> blockContents) {
        Map<String, Object> content = new HashMap<>();
        content.put("blocks", blockContents);

        SaveContent saveContent = SaveContent.builder().content(content).build();

        SaveContent saved = saveContentRepository.save(saveContent);
        return saved.getId(); // MongoDB ObjectId

    }

    /**
     * RDB에 저장 정보를 저장하고, 실패 시 MongoDB에 저장된 내용도 롤백합니다.
     */
    private Save saveToRDB(Branch branch, String saveMongoId) {
        try {
            Save save = Save.builder().branch(branch).saveMongoId(saveMongoId).build();
            return saveRepository.save(save);
        } catch (DataAccessException e) {
            // RDB 저장 실패 → MongoDB 저장 롤백 시도
            try {
                saveContentRepository.deleteById(saveMongoId);
            } catch (Exception deleteEx) {
                log.error("Mongo SaveContent(id={})  RDB 저장 실패 후 Mongo 삭제까지 실패함", saveMongoId,
                        deleteEx);
            }
            throw new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_RDB);
        }
    }
}

