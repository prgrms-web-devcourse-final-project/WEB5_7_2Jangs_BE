package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.merge.app.MergeService.MergeContext;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteOutboxFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MergeOrchestrator {

    private final MergeMongoTxService mergeMongoTxService;
    private final MergeMySqlTxService mergeMySqlTxService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public MergeResponse merge(MergeContext context, MergeRequest request) {
        String saveMongoId = mergeMongoTxService.createMongoPart(request.content());
        try {
            return mergeMySqlTxService.createMySqlPart(
                    context,
                    request,
                    saveMongoId
            );
        } catch (Exception e) {
            log.warn("[SAGA] 머지용 브랜치/작업장 생성 실패 -> Mongo 삭제 Outbox 기록.", e);
            MongoIdsDto compensateMongoIds = new MongoIdsDto(
                    List.of(saveMongoId),
                    null,
                    null
            );
            mongoDeleteOutboxFactory.createMergeCompensation(saveMongoId, compensateMongoIds);
            throw new CustomException(CommitErrorCode.FAIL_MERGE);
        }
    }
}
