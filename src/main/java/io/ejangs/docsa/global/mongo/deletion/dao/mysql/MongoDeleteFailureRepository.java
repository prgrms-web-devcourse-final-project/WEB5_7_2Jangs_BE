package io.ejangs.docsa.global.mongo.deletion.dao.mysql;

import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MongoDeleteFailureRepository extends JpaRepository<MongoDeleteOutbox, Long> {

    List<MongoDeleteOutbox> findAllByResolvedIsFalse();
}
