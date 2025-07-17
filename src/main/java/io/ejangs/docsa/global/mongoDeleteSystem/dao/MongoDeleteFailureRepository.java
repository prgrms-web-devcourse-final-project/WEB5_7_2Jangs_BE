package io.ejangs.docsa.global.mongoDeleteSystem.dao;

import io.ejangs.docsa.global.mongoDeleteSystem.entity.MongoDeleteFailure;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoDeleteFailureRepository extends MongoRepository<MongoDeleteFailure, Long> {
    
}
