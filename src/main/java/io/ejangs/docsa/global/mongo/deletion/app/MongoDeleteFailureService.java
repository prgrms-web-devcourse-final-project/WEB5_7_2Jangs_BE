package io.ejangs.docsa.global.mongo.deletion.app;

import io.ejangs.docsa.global.mongo.deletion.dao.mysql.MongoDeleteFailureRepository;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDeleteFailureService {

    private final MongoDeleteFailureRepository failureRepository;

    @Async
    @Transactional
    public void saveFailure(MongoIdsDto dto) {
        try {
            MongoDeleteOutbox failure = MongoDeleteOutbox.builder()
                    .saveContentIds(dto.saveContentsIds())
                    .commitBlockSequenceIds(dto.commitBlockSequenceIds())
                    .blockIds(dto.blockIds())
                    .build();

            failureRepository.save(failure);
            log.info("Mongo 삭제 실패 정보 저장 성공");
        } catch (Exception e) {
            log.error("실패내역 저장 중 예외 발생: {}", e.getMessage(), e);
        }
    }
}
