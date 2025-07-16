package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.app.UserService;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static io.ejangs.docsa.domain.branch.util.RenewUpdatedAtHelper.touch;


@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final CommitRepository commitRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;
    private final UserService userService;
    private final DocService docService;

    private final CommitContentAssembler commitContentAssembler;

    /**
     * '이어서 작업하기' 로직으로, 브랜치를 생성하고 저장을 추가하거나 기존 브랜치에 저장을 추가합니다.
     * <p>
     * fromCommitId가 존재하면 기존 커밋에서 분기(branch)를 만들거나 저장(save)을 추가하는 상황입니다. fromCommitId가 null이면 최초 브랜치
     * 생성으로, 이 경우는 doc 도메인에서 처리합니다.
     */

    @Transactional
    public BranchCreateResponse createBranchOrSave(Long documentId, BranchCreateRequest request,
            Long userId) {

        userService.checkUserOrThrow(userId);
        docService.getDocByIdAndUserId(documentId, userId);

        Long fromCommitId = request.fromCommitId();

        // fromCommitId가 null이면 최초 브랜치 생성 시도 -> 여기서는 허용하지 않음
        if (fromCommitId == null) {
            throw new CustomException(CommitErrorCode.INVALID_FROM_COMMIT);
        }

        // 분기 기준이 되는 커밋을 조회
        Commit fromCommit = commitRepository.findById(fromCommitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));

        Branch fromBranch = fromCommit.getBranch();

        // 커밋이 요청한 문서에 속해 있는지 검증
        if (!fromBranch.getDoc().getId().equals(documentId)) {
            throw new CustomException(DocErrorCode.COMMIT_NOT_IN_DOCUMENT);
        }

        // fromCommit이 브랜치의 최신 커밋(leaf)인지 여부 판단
        boolean isLeaf = fromBranch.getLeafCommit() != null && fromBranch.getLeafCommit().getId()
                .equals(fromCommitId);

        if (isLeaf) {
            // 커밋이 브랜치의 최신 커밋인 경우 → 기존 브랜치에 새로운 저장(save)만 추가
            Save save = createSave(fromBranch, fromCommit.getCommitMongoId());

            // 저장과 브랜치, 문서의 updatedAt 동시 갱신
            touch(save);
            return BranchMapper.toBranchCreateResponse(fromBranch, save);
        } else {
            // 중간 커밋에서 작업을 이어가는 경우 → 새로운 브랜치를 생성하고 저장도 함께 생성
            Branch newBranch = Branch.builder().name(request.name()).doc(fromBranch.getDoc())
                    .fromCommit(fromCommit).build();

            fromBranch.getDoc().addBranch(newBranch);
            branchRepository.save(newBranch);

            Save save = createSave(newBranch, fromCommit.getCommitMongoId());

            // 저장과 브랜치, 문서의 updatedAt 동시 갱신
            touch(save);
            return BranchMapper.toBranchCreateResponse(newBranch, save);
        }
    }

    /**
     * 새로운 저장(Save)을 생성합니다.
     * <p>
     * 1. 먼저 RDB에 Save 엔티티를 저장합니다 (MongoId는 null). 2. 이후 MongoDB에 내용을 저장합니다. 3. Mongo 저장 성공 시, 해당
     * MongoId를 RDB Save 엔티티에 업데이트합니다.
     */
    private Save createSave(Branch branch, String commitMongoId) {
        Save save = saveToRDB(branch); // RDB에 먼저 저장
        saveContentToMongoAndUpdateRDB(save, commitMongoId); // Mongo 저장 + RDB에 mongoId 반영
        return save;
    }

    /**
     * Save 엔티티를 MongoId 없이 RDB에 먼저 저장합니다.
     */
    private Save saveToRDB(Branch branch) {
        Save save = Save.builder().branch(branch).build();
        return saveRepository.save(save);
    }

    /**
     * 커밋의 블록을 조립해 MongoDB에 저장하고, 그 결과로 받은 mongoId를 RDB save에 반영(update)합니다.
     * <p>
     * Mongo 저장 실패 시 예외가 발생하고 트랜잭션 전체가 롤백됩니다.
     */
    private void saveContentToMongoAndUpdateRDB(Save save, String commitMongoId) {
        try {
            List<Map<String, Object>> blockContents =
                    commitContentAssembler.assemble(commitMongoId);

            List<SaveBlock> saveBlocks = blockContents.stream().map(SaveBlock::from).toList();

            SaveContent saveContent = SaveContent.builder().content(saveBlocks).build();
            SaveContent saved = saveContentRepository.save(saveContent);

            // mongoId를 RDB Save 엔티티에 설정
            save.updateSaveMongoId(saved.getId());
            // 트랜잭션 내에서 JPA의 더치체킹으로 update 됨
        } catch (Exception e) {
            log.error("MongoDB 저장 실패로 인해 save(id={}) 에 MongoId 갱신 실패", save.getId(), e);
            throw new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_MONGO);
        }
    }

    public Branch findById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));
    }
}


