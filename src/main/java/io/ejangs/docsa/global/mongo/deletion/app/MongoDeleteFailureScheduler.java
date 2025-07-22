package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteFailure;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDeleteFailureScheduler {

    private final MongoDeleteFailureRepository mongoDeleteFailureRepository;
    private final MongoDeleteRetryService mongoDeleteRetryService;

    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24 * 7)
    @Transactional(transactionManager = "mongoTransactionManager")
    public void run() {
        List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAllByResolvedIsFalse();

        for (MongoDeleteFailure failure : failures) {
            try {
                MongoIdsDto dto = new MongoIdsDto(
                        failure.getSaveContentIds(),
                        failure.getCommitBlockSequenceIds(),
                        failure.getBlockIds()
                );
                mongoDeleteRetryService.deleteMongoData(dto);
                failure.markResolved();
            } catch (Exception e) {
                log.error("Mongo 삭제 스케쥴러 재시도 실패 : {}", e.getMessage(), e);
            }
        }
        mongoDeleteFailureRepository.saveAll(failures);
    }

}
