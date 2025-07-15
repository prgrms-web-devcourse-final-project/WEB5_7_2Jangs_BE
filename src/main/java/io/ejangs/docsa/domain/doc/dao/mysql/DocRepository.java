package io.ejangs.docsa.domain.doc.dao.mysql;

import io.ejangs.docsa.domain.doc.entity.Doc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocRepository extends JpaRepository<Doc, Long> {

    Optional<Doc> getDocByIdAndUserId(Long id, Long userId);

    Boolean existsByUserIdAndTitle(Long userId, String title);

    List<Doc> findAllByUserId(Long userId);
}

