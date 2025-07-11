package io.ejangs.docsa.domain.save.save;

import io.ejangs.docsa.domain.save.entity.Save;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SaveRepository extends JpaRepository<Save, Long> {

    Optional<Save> findByBranchId(Long branchId);
}
