package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
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

    public Commit saveAndFlush(Commit commit) {
        return commitRepository.saveAndFlush(commit);
    }

    public void deleteById(Long commitId) {
        commitRepository.deleteById(commitId);
    }

    public CommitBlockSequence saveCommitBlockSequence(CommitBlockSequence commitBlockSequence) {
        return commitBlockSequenceRepository.save(commitBlockSequence);
    }

    public void deleteCbsById(String cbsId) {
        commitBlockSequenceRepository.deleteById(cbsId);
    }

}
