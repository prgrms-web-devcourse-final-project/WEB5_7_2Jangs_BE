package io.ejangs.docsa.domain.doc.readmodel.dao.mongodb;

import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocListReadModelRepository extends MongoRepository<DocListReadModel, Long> {

    Page<DocListReadModel> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    Page<DocListReadModel> findByUserIdAndDeletedFalseAndTitleContainingIgnoreCase(
            Long userId,
            String title,
            Pageable pageable
    );

}
