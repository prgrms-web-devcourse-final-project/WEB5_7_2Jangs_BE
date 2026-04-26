package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.app.create.BranchCreateOrchestrator;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.branch.util.BranchMapper;
import io.ejangs.docsa.domain.commit.app.CommitQueryService;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.app.create.DocQueryService;
import io.ejangs.docsa.domain.edge.app.EdgeService;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.outbox.mongo.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.outbox.mongo.util.MongoDeleteMapper;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;

import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final DocQueryService docQueryService;
    private final BranchQueryService branchQueryService;
    private final CommitQueryService commitQueryService;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final EdgeService edgeService;
    private final BranchCreateOrchestrator branchCreateOrchestrator;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public BranchCreateResponse createBranch(Long documentId, BranchCreateRequest request,
            Long userId) {

        BranchCreateContext context = prepareBranchCreateContext(documentId, request, userId);

        return branchCreateOrchestrator.create(context);
    }

    private BranchCreateContext prepareBranchCreateContext(Long documentId,
            BranchCreateRequest request, Long userId) {
        docQueryService.checkByIdAndUserId(documentId, userId);

        Long fromCommitId = request.fromCommitId();

        Commit fromCommit = commitQueryService.getById(fromCommitId);
        Branch fromBranch = fromCommit.getBranch();

        if (!fromBranch.getDoc().getId().equals(documentId)) {
            throw new CustomException(DocErrorCode.COMMIT_NOT_IN_DOCUMENT);
        }

        branchQueryService.checkDuplicatedWithBranchName(documentId, request.name());


        return new BranchCreateContext(
                fromBranch.getDoc(),
                fromBranch,
                fromCommit,
                request.name(),
                fromCommit.getCommitMongoId()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public BranchRenameResponse renameBranch(Long documentId, Long branchId, String newName,
            Long userId) {

        // 1. 브랜치 검증
        branchQueryService.checkBranchInDocOwnedByUser(documentId, branchId, userId);
        Branch branch = branchQueryService.getById(branchId);
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(Long documentId, Long branchId, Long userId) {

        // 1. 브랜치 검증
        branchQueryService.checkBranchInDocOwnedByUser(documentId, branchId, userId);
        Branch branch = branchQueryService.getById(branchId);

        // 2. main브랜치는 삭제가 불가능하도록 함
        checkDefaultBranch(branch);

        // 3. 삭제하려는 브랜치의 커밋 중 다른 브랜치의 fromCommit이 없는지 확인
        List<Commit> branchCommits = branch.getCommits();
        List<Long> commitsIds = branchCommits.stream().map(Commit::getId).toList();

        if (branchQueryService.existsSubBranchByFromCommitIds(commitsIds)) {
            throw new CustomException(BranchErrorCode.SUB_BRANCH_DELETE_UNAVAILABLE);
        }

        // 4. Edge 삭제
        edgeService.deleteEdgesConnectedToCommits(commitsIds);

        // 5. 브랜치에서 삭제 가능한 블록과 시퀀스, SaveContent 삭제 이벤트 발행
        MongoIdsDto deletableMongoIds = collectDeletableMongoDataForBranch(branch, branchCommits);

        // 6. 브랜치가 속한 문서의 수정시간 갱신
        RenewUpdatedAtHelper.touch(branch);

        // 7. Doc의 branch 컬렉션에서 branch 수동 삭제
        Doc doc = branch.getDoc();
        doc.getBranches().remove(branch);

        // 8. 브랜치, 나머지 RDB  브랜치 메타데이터 CASCADE 삭제
        branchQueryService.delete(branch);

        mongoDeleteOutboxFactory.create(
                TriggerType.DELETE,
                DomainType.BRANCH,
                OriginType.BRANCH_ID,
                branchId,
                deletableMongoIds
        );

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

    private void checkDefaultBranch(Branch branch) {
        if (branch.getFromCommit() == null) {
            throw new CustomException(BranchErrorCode.MAIN_BRANCH_FIX_UNAVAILABLE);
        }
    }

}
