package io.ejangs.docsa.domain.commit.dao;

import io.ejangs.docsa.domain.commit.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<Commit, Long> {

}
