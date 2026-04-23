package io.ejangs.docsa.domain.doc.thumbnail.dao;

import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {

}
