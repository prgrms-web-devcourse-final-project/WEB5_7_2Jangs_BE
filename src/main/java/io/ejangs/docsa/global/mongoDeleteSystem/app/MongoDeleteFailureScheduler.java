package io.ejangs.docsa.global.mongoDeleteSystem.app;

import io.ejangs.docsa.global.mongoDeleteSystem.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongoDeleteSystem.dto.DocDeleteMongoIdsDto;
import io.ejangs.docsa.global.mongoDeleteSystem.entity.MongoDeleteFailure;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDeleteFailureScheduler {

    private final MongoDeleteFailureRepository mongoDeleteFailureRepository;
    private final MongoDeleteRetryService mongoDeleteRetryService;

    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24 * 7)
    public void run() {
        List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAllByResolvedIsFalse();

        for (MongoDeleteFailure failure : failures) {
            try {
                DocDeleteMongoIdsDto dto = new DocDeleteMongoIdsDto(
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
