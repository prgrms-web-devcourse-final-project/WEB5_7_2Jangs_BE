package io.ejangs.docsa.domain.block.dao.mongodb;

import io.ejangs.docsa.domain.block.document.Block;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlockRepository extends MongoRepository<Block, String> {

}
