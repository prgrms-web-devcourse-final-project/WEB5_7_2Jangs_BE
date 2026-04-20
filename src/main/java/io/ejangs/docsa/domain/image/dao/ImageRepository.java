package io.ejangs.docsa.domain.image.dao;

import io.ejangs.docsa.domain.image.entity.Image;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByIdAndUserId(Long imageId, Long userId);
}
