package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.doc.dao.mysql.EdgeRepository;
import io.ejangs.docsa.domain.doc.entity.Edge;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.util.MongoDeleteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;



@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final CommitRepository commitRepository;
    private final BranchRepository branchRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;
    private final DocRepository docRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;
    private final EdgeRepository edgeRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final CommitContentAssembler commitContentAssembler;

    @Value("${default.branch}")
    private String defaultBranchName;

    /**
     * '이어서 작업하기' 로직으로, 브랜치를 생성하고 저장을 추가하거나 기존 브랜치에 저장을 추가합니다.
     * <p>
     * fromCommitId가 존재하면 기존 커밋에서 브랜치를 만들거나 저장(save)을 추가하는 상황입니다. fromCommitId가 null이면 최초 브랜치 생성으로,
     * 이 경우는 doc 도메인에서 처리합니다.
     */

    @Transactional
    public BranchCreateResponse createBranchOrSave(Long documentId, BranchCreateRequest request,
            Long userId) {

        checkDocByIdAndUserId(documentId, userId);

        Long fromCommitId = request.fromCommitId();

        // 1. fromCommitId가 null이면 최초 브랜치 생성 시도 -> 여기서는 허용하지 않음
        if (fromCommitId == null) {
            throw new CustomException(CommitErrorCode.INVALID_FROM_COMMIT);
        }

        // 2. '이어서 작업하기' 를 시도하는 커밋 검증
        Commit fromCommit = checkById(fromCommitId);

        Branch fromBranch = fromCommit.getBranch();

        // 3. 커밋이 요청한 문서에 속해 있는지 검증
        if (!fromBranch.getDoc().getId().equals(documentId)) {
            throw new CustomException(DocErrorCode.COMMIT_NOT_IN_DOCUMENT);
        }

        // 4. '이어서 작업하기' 를 시도하려는 커밋이 브랜치의 최신 커밋(leaf)인지 여부 판단
        boolean isLeaf = fromBranch.getLeafCommit() != null && fromBranch.getLeafCommit().getId()
                .equals(fromCommitId);

        if (isLeaf) {
            // 커밋이 브랜치의 최신 커밋인 경우 → 기존 브랜치에 새로운 저장(save)만 추가
            Save save = createSave(fromBranch, fromCommit.getCommitMongoId());

            // 저장과 브랜치, 문서의 updatedAt 동시 갱신
            RenewUpdatedAtHelper.touch(save);
            return BranchMapper.toBranchCreateResponse(fromBranch, save);

        } else {
            // 중간 커밋에서 작업을 이어가는 경우 → 새로운 브랜치를 생성하고 저장도 함께 생성
            Branch newBranch = Branch.builder().name(request.name()).doc(fromBranch.getDoc())
                    .fromCommit(fromCommit).build();

            fromBranch.getDoc().addBranch(newBranch);
            branchRepository.save(newBranch);

            Save save = createSave(newBranch, fromCommit.getCommitMongoId());

            // 저장과 브랜치, 문서의 updatedAt 동시 갱신
            RenewUpdatedAtHelper.touch(save);
            return BranchMapper.toBranchCreateResponse(newBranch, save);
        }
    }

    /**
     * 새로운 저장(Save)을 생성합니다.
     * <p>
     * 먼저 RDB에 Save의 메타데이터 저장 후, MongoDB 저장이 성공하면 MongoId를 RDB Save 엔티티에 업데이트합니다.
     */
    private Save createSave(Branch branch, String commitMongoId) {
        Save save = saveToRDB(branch); // RDB에 먼저 저장
        saveContentToMongoAndUpdateRDB(save, commitMongoId); // Mongo 저장 + RDB에 mongoId 반영
        return save;
    }

    private Save saveToRDB(Branch branch) {
        Save save = Save.builder().branch(branch).build();
        return saveRepository.save(save);
    }

    /**
     * 커밋의 블록을 조립해 MongoDB에 저장하고, 그 결과로 받은 mongoId를 RDB save에 반영합니다.
     * <p>
     * Mongo 저장 실패 시 예외가 발생하고 트랜잭션 전체가 롤백됩니다.
     */
    private void saveContentToMongoAndUpdateRDB(Save save, String commitMongoId) {
        try {
            List<Map<String, Object>> blockContents =
                    commitContentAssembler.assemble(commitMongoId);

            SaveContent saveContent = SaveContent.builder().content(blockContents).build();
            SaveContent saved = saveContentRepository.save(saveContent);

            // mongoId를 RDB Save 엔티티에 설정
            save.updateSaveMongoId(saved.getId());
            // 트랜잭션 내에서 JPA의 더치체킹으로 update 됨
        } catch (Exception e) {
            // MongoDB 롤백까지 실패할 경우 에러 로그
            log.error("MongoDB 저장 실패로 인해 save(id={}) 에 MongoId 갱신 실패", save.getId(), e);
            throw new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_MONGO);
        }
    }

    @Transactional
    public BranchRenameResponse renameBranch(Long documentId, Long branchId, String newName,
            Long userId) {

        // 1. 브랜치 검증
        checkBranchInDocOwnedByUser(documentId, branchId, userId);
        Branch branch = getById(branchId);
        checkDefaultBranch(branch);

        // 2. 브랜치 이름 수정 후 브랜치와 문서의 수정시각 갱신
        branch.updateName(newName);
        RenewUpdatedAtHelper.touch(branch);

        return BranchMapper.toBranchRenameResponse(branch);

    }

    /**
     * 브랜치 삭제 기능입니다.
     * <p>
     * 삭제하려는 브랜치는 메인 브랜치가 아니며 파생된 서브브랜치 또한 가지고 있지 않아야 합니다. 삭제 가능한 브랜치임을 확인 후 오직 해당 브랜치에서만 존재하는 블록을
     * 삭제한 후 나머지 브랜치 관련 정보를 삭제합니다.
     */
    @Transactional
    public void deleteBranch(Long documentId, Long branchId, Long userId) {

        // 1. 브랜치 검증
        checkBranchInDocOwnedByUser(documentId, branchId, userId);
        Branch branch = getById(branchId);

        // 2. main브랜치는 삭제가 불가능하도록 함
        checkDefaultBranch(branch);

        // 3. 삭제하려는 브랜치의 커밋 중 다른 브랜치의 fromCommit이 없는지 확인
        List<Commit> branchCommits = branch.getCommits();
        List<Long> commitsIds = branchCommits.stream().map(Commit::getId).toList();

        if (branchRepository.existsByFromCommitIdIn(commitsIds)) {
            throw new CustomException(BranchErrorCode.SUB_BRANCH_DELETE_UNAVAILABLE);
        }

        // 4. Edge 삭제
        List<Edge> edgesToDelete =
                edgeRepository.findAllByPrevCommitIdInOrNextCommitIdIn(commitsIds, commitsIds);
        edgeRepository.deleteAll(edgesToDelete);

        // 5. 브랜치에서 삭제 가능한 블록과 시퀀스, SaveContent 삭제 이벤트 발행
        MongoIdsDto deletableMongoIds = collectDeletableMongoDataForBranch(branch, branchCommits);
        eventPublisher.publishEvent(deletableMongoIds);

        // 6. 브랜치가 속한 문서의 수정시간 갱신
        RenewUpdatedAtHelper.touch(branch);

        // 7. 브랜치, 나머지 RDB  브랜치 메타데이터 CASCADE 삭제
        branchRepository.delete(branch);

    }


    /**
     * 브랜치에서 삭제 가능한 SaveContent와 블록, 시퀀스를 찾아 반환합니다.
     */
    private MongoIdsDto collectDeletableMongoDataForBranch(Branch branch,
            List<Commit> branchCommits) {

        List<String> sequenceIdsToDelete = new ArrayList<>();
        Set<String> allBlockIds = new HashSet<>();

        for (Commit commit : branchCommits) {
            // 커밋 시퀀스 id 수집
            String seqId = commit.getCommitMongoId();
            if (seqId != null) {
                sequenceIdsToDelete.add(seqId);
                commitBlockSequenceRepository.findById(seqId).ifPresent(seq -> {
                    // 브랜치가 가진 모든 블록 id 수집
                    allBlockIds.addAll(seq.getBlockOrders());
                });

            }
        }

        // 삭제 대상에서 제외하기 위한 from 커밋의 블록(브랜치 생성 이전 존재하던 블록) 필터링
        Set<String> baseBlockIds = new HashSet<>();
        if (branch.getFromCommit() != null) {
            String baseSeqId = branch.getFromCommit().getCommitMongoId();
            if (baseSeqId != null) {
                commitBlockSequenceRepository.findById(baseSeqId).ifPresent(seq -> {
                    baseBlockIds.addAll(seq.getBlockOrders());
                });
            }
        }

        // 차집합 남기기 {브랜치에 속한 커밋에 존재하는 모든 blockId} - {브랜치의 from_commit 에 존재하는 모든 blockid}
        allBlockIds.removeAll(baseBlockIds);

        return MongoDeleteMapper.toMongoIdsDto(branch, sequenceIdsToDelete,
                new ArrayList<>(allBlockIds));

    }

    //  브랜치 관련 검증로직 메서드들

    public Branch getById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new CustomException(BranchErrorCode.BRANCH_NOT_FOUND));
    }

    public void checkBranchInDocOwnedByUser(Long documentId, Long branchId, Long userId) {
        boolean exists =
                branchRepository.existsByIdAndDocIdAndDocUserId(branchId, documentId, userId);
        if (!exists) {
            throw new CustomException(BranchErrorCode.BRANCH_NOT_FOUND_OR_FORBIDDEN);
        }
    }

    public void checkDocByIdAndUserId(Long docId, Long userId) {
        if (!docRepository.existsByIdAndUserId(docId, userId))
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
    }

    private Commit checkById(Long commitId) {
        return commitRepository.findById(commitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }
    private void checkDefaultBranch(Branch branch) {
        if (branch.getName().equals(defaultBranchName)) {
            throw new CustomException(BranchErrorCode.MAIN_BRANCH_DELETE_UNAVAILABLE);
        }
    }

    public Branch saveBranch(Branch branch) {
        return branchRepository.save(branch);

    }
}



