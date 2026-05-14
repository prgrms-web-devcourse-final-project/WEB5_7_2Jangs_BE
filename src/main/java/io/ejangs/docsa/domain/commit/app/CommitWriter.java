package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.commit.dao.mysql.CommitRepository;
import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import io.ejangs.docsa.domain.commit.entity.Commit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommitWriter {

    private final CommitRepository commitRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;

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
