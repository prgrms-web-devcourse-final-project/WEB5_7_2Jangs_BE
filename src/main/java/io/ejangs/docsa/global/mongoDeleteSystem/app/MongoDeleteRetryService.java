package io.ejangs.docsa.global.mongoDeleteSystem.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.global.mongoDeleteSystem.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongoDeleteSystem.dto.DocDeleteMongoIdsDto;
import io.ejangs.docsa.global.mongoDeleteSystem.entity.MongoDeleteFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDeleteRetryService {

    private int retryCnt = 1;

    private final SaveContentRepository saveContentRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;

    private final MongoDeleteFailureRepository mongoDeleteFailureRepository;

    @Retryable(
            retryFor = {Exception.class},
            recover = "recover",
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000) // 2초 간격 재시도
    )
    @Transactional(transactionManager = "mongoTransactionManager")
    public void deleteMongoData(DocDeleteMongoIdsDto dto) {
        log.info("MongoDelete Retry : {}", retryCnt++);

        for (String saveContentId : dto.saveContentsIds()) {
            saveContentRepository.deleteById(saveContentId);
        }
        for (String commitBlockSequenceId : dto.commitBlockSequenceIds()) {
            commitBlockSequenceRepository.deleteById(commitBlockSequenceId);
        }
        for (String blockId : dto.blockIds()) {
            blockRepository.deleteById(blockId);
        }
    }

    @Recover
    public void recover(Exception e, DocDeleteMongoIdsDto dto) {
        log.error("Mongo 삭제 3회 재시도 실패 - {}", e.getMessage());
        try {
            MongoDeleteFailure failure = MongoDeleteFailure.builder()
                    .saveContentIds(dto.saveContentsIds())
                    .commitBlockSequenceIds(dto.commitBlockSequenceIds())
                    .blockIds(dto.blockIds())
                    .build();

            mongoDeleteFailureRepository.saveAndFlush(failure);
            log.info("Mongo삭제 실패 정보 저장 성공");
        } catch (Exception ex) {
            log.error("복구 중 예외 발생: {}", ex.getMessage(), ex);
        }
    }
}
