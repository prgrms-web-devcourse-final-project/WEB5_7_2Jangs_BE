package io.ejangs.docsa.domain.save.dao;

import io.ejangs.docsa.domain.save.entity.Save;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaveRepository extends JpaRepository<Save, Long> {

}
