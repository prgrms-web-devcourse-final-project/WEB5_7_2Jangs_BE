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
    //TODO
    // 스케줄러 재시도 실패 시 동일 MongoIdsDto가 중복 저장되는 문제 개선 (중복 방지 키 또는 상태 업데이트 방식으로 전환)
	// 스케줄러의 트랜잭션 매니저 분리 (RDB는 JPA, Mongo는 mongoTransactionManager로 역할 명확화)
    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24 * 7)
    @Transactional(transactionManager = "mongoTransactionManager")
    public void run() {
        List<MongoDeleteFailure> failures = mongoDeleteFailureRepository.findAllByResolvedIsFalse();
        log.info("스케쥴러 작동 : {}", failures.size());
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
