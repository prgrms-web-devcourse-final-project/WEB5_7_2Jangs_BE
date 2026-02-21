package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.app.DocQueryService;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitQueryService {

    private final CommitRepository commitRepository;
    private final DocQueryService docQueryService;
    private final CommitContentAssembler assembler;

    @Transactional(readOnly = true)
    public CommitResponse getCommit(Long docId, Long commitId, Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> assemble = getWholeContent(commitId);
        return CommitMapper.toCommitResponse(assemble);
    }

    @Transactional(readOnly = true)
    public CompareMergeCommitResponse compareCommitForMerge(Long docId, Long baseId, Long targetId,
            Long userId) {
        docQueryService.checkByIdAndUserId(docId, userId);
        List<Map<String, Object>> baseContent = getWholeContent(baseId);
        List<Map<String, Object>> targetContent = getWholeContent(targetId);
        return CommitMapper.toCompareMergeCommitResponse(baseContent, targetContent);
    }

    @Transactional(readOnly = true)
    public Commit getById(Long commitId) {
        return commitRepository.findById(commitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Commit getLeafCommit(Branch branch) {
        return Optional.ofNullable(branch.getLeafCommit())
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public String resolveBaseCommitCbsMongoId(Branch branch) {
        Long baseCommitId = Optional.ofNullable(branch.getLeafCommit())
                .map(Commit::getId)
                .orElseGet(() -> Optional.ofNullable(branch.getFromCommit())
                        .map(Commit::getId)
                        .orElse(null));
        if (baseCommitId == null) {
            return null;
        }
        return commitRepository.findById(baseCommitId)
                .map(Commit::getCommitMongoId)
                .orElse(null);
    }

    public void checkLeafCommit(Commit commit) {
        if (!commit.getId().equals(commit.getBranch().getLeafCommit().getId())) {
            throw new CustomException(CommitErrorCode.IS_NOT_LEAF_COMMIT);
        }
    }

    private List<Map<String, Object>> getWholeContent(Long commitId) {
        Commit commit = getById(commitId);
        return assembler.assemble(commit.getCommitMongoId());
    }
}
