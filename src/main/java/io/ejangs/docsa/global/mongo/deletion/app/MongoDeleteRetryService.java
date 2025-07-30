package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDeleteRetryService {


    private final SaveContentRepository saveContentRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;

    private final MongoDeleteFailureService mongoDeleteFailureService;

    @Retryable(
            retryFor = {Exception.class},
            recover = "recover",
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000) // 2초 간격 재시도
    )
    @Transactional(transactionManager = "mongoTransactionManager")
    public void deleteMongoData(MongoIdsDto dto) {
        int retryCount = RetrySynchronizationManager.getContext() != null
                ? RetrySynchronizationManager.getContext().getRetryCount()
                : 0;
        log.warn("[MONGO] MongoDelete Retry count = {}", retryCount);

        for (String saveContentId : dto.saveContentsIds()) {
            saveContentRepository.deleteById(saveContentId);
            log.warn("[MONGO] MongoDelete Save Content id = {}", saveContentId);
        }
        for (String commitBlockSequenceId : dto.commitBlockSequenceIds()) {
            commitBlockSequenceRepository.deleteById(commitBlockSequenceId);
            log.warn("[MONGO] MongoDelete Commit Block Sequence id = {}", commitBlockSequenceId);
        }
        for (String blockId : dto.blockIds()) {
            blockRepository.deleteById(blockId);
            log.warn("[MONGO] MongoDelete Block id = {}", blockId);
        }
    }

    @Recover
    public void recover(Exception e, MongoIdsDto dto) {
        log.error("Mongo 삭제 3회 재시도 실패 - {}", e.getMessage());
        mongoDeleteFailureService.saveFailure(dto);
    }
}
