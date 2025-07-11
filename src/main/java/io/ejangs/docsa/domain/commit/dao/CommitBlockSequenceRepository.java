package io.ejangs.docsa.domain.commit.dao;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.entity.CommitBlockSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitBlockSequenceRepository extends JpaRepository<CommitBlockSequence, Long> {
    List<CommitBlockSequence> findByCommit(Commit commit);
}
