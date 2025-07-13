package io.ejangs.docsa.domain.save.dao.mongodb;

import io.ejangs.docsa.domain.save.document.SaveContent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SaveContentRepository extends MongoRepository<SaveContent, String> {

}
