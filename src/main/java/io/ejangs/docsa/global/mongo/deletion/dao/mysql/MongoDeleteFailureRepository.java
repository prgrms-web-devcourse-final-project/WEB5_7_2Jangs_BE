package io.ejangs.docsa.global.mongo.deletion.dao.mysql;

import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteFailure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MongoDeleteFailureRepository extends JpaRepository<MongoDeleteFailure, Long> {

    List<MongoDeleteFailure> findAllByResolvedIsFalse();
}
