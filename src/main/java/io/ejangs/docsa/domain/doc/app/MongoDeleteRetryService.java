package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.block.dao.mongodb.BlockRepository;
import io.ejangs.docsa.domain.commit.dao.mongodb.CommitBlockSequenceRepository;
import io.ejangs.docsa.domain.doc.dto.DocDeleteMongoIdsDto;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
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

    private final SaveContentRepository saveContentRepository;
    private final CommitBlockSequenceRepository commitBlockSequenceRepository;
    private final BlockRepository blockRepository;

    @Transactional
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000) // 2초 간격 재시도
    )
    public void deleteMongoData(DocDeleteMongoIdsDto dto) {
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
        // 실패 정보 DB 저장
    }
}
