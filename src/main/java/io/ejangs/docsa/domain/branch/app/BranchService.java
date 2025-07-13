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

    @Transactional
    public BranchCreateResponse createBranchOrSave(Long documentId, BranchCreateRequest request) {

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

                Save save = createSave(newBranch, fromCommit.getCommitMongoId());
                return BranchMapper.toBranchCreateResponse(newBranch, save);
            }
        }

        // 최초의 브랜치 생성 이외에는 request.fromCommitId != null
        throw new CustomException(CommitErrorCode.INVALID_FROM_COMMIT);

    }

    // 저장할 본문 assembler로 조립해 Mongo와 RDB에 저장
    private Save createSave(Branch branch, String commitMongoId) {
        List<Map<String, Object>> blockContents = commitContentAssembler.assemble(commitMongoId);
        String mongoId = saveContentToMongo(blockContents);
        return saveToRDB(branch, mongoId);

    }

    //MongoDB 저장 담당 메서드
    private String saveContentToMongo(List<Map<String, Object>> blockContents) {
        Map<String, Object> content = new HashMap<>();
        content.put("blocks", blockContents);

        SaveContent saveContent = SaveContent.builder().content(content).build();

        SaveContent saved = saveContentRepository.save(saveContent);
        return saved.getId();

    }

    // RDB 저장과 Mongo 저장 실패시 롤백 담당 메서드
    private Save saveToRDB(Branch branch, String saveMongoId) {
        try {
            Save save = Save.builder().branch(branch).saveMongoId(saveMongoId).build();
            return saveRepository.save(save);
        } catch (DataAccessException e) { //  RDB 저장  예외 처리
            try { // Mongo에 저장된 내용 롤백 시도
                saveContentRepository.deleteById(saveMongoId);
            } catch (Exception deleteEx) {
                log.warn("Mongo SaveContent(id={})  RDB 저장 실패 후 Mongo 삭제까지 실패함",
                        saveMongoId, deleteEx);
            }
            throw new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_RDB);
        }
    }
}

