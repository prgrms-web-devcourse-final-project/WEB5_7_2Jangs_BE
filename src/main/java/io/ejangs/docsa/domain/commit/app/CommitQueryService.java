package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitQueryService {

    private final CommitRepository commitRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;

    @Transactional(readOnly = true)
    public Commit getById(Long commitId) {
        return commitRepository.findById(commitId)
                .orElseThrow(() -> new CustomException(CommitErrorCode.COMMIT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<String> findCommitMongoIdById(Long commitId) {
        return commitRepository.findById(commitId)
                .map(Commit::getCommitMongoId)
                .filter(id -> !id.isBlank());
    }

    @Transactional(readOnly = true)
    public List<CommitGraphDto> getCommitGraphList(Long docId) {
        return commitRepository.getCommitGraphList(docId);
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
        return findCommitMongoIdById(baseCommitId).orElse(null);
    }

    @Transactional(readOnly = true)
    public void checkTwoCommitsInDocOwnedByUser(Long commitId1, Long commitId2, Long documentId, Long userId) {
        if (commitId1.equals(commitId2)) {
            throw new CustomException(CommitErrorCode.INVALID_MERGE_REQUEST);
        }

        long count = commitRepository.countCommitsInOwnedDoc(
                List.of(commitId1, commitId2),
                documentId,
                userId
        );

        if (count != 2) {
            throw new CustomException(CommitErrorCode.COMMIT_NOT_FOUND);
        }
    }

    public Commit saveAndFlush(Commit commit) {
        return commitRepository.saveAndFlush(commit);
    }

    public void deleteById(Long commitId) {
        commitRepository.deleteById(commitId);
    }

    public CommitBlockSequence saveCommitBlockSequence(CommitBlockSequence commitBlockSequence) {
        return commitBlockSequenceRepository.save(commitBlockSequence);
    }

}
