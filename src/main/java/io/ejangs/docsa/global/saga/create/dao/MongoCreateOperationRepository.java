package io.ejangs.docsa.global.saga.create.dao;

import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperation;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MongoCreateOperationRepository extends JpaRepository<MongoCreateOperation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MongoCreateOperation> findWithLockByOperationId(String operationId);

    List<MongoCreateOperation> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            MongoCreateOperationStatus status,
            LocalDateTime updatedAt
    );
}
