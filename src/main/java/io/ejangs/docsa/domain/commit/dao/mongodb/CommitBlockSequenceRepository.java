package io.ejangs.docsa.domain.commit.dao.mongodb;

import io.ejangs.docsa.domain.commit.document.CommitBlockSequence;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommitBlockSequenceRepository extends
        MongoRepository<CommitBlockSequence, String> {

}
