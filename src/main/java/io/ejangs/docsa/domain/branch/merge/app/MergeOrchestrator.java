package io.ejangs.docsa.domain.branch.merge.app;

import io.ejangs.docsa.domain.branch.merge.app.MergeService.MergeContext;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.CommitErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.saga.create.app.MongoCreateCompensationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationService;
import io.ejangs.docsa.global.saga.create.app.MongoCreateOperationStart;
import io.ejangs.docsa.global.saga.create.entity.MongoCreateOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MergeOrchestrator {

    private final MergeMongoTxService mergeMongoTxService;
    private final MergeMySqlTxService mergeMySqlTxService;
    private final MongoCreateOperationService operationService;
    private final MongoCreateCompensationService compensationService;

    public MergeResponse merge(
            MergeContext context,
            MergeRequest request,
            Long userId,
            String operationId,
            String requestHash,
            MongoIdsDto plan
    ) {
        MongoCreateOperationStart start = operationService.start(
                operationId, userId, MongoCreateOperationType.MERGE, requestHash, plan
        );
        if (!start.started()) {
            return new MergeResponse(start.resultEntityId(), start.resultSaveId());
        }

        try {
            String saveMongoId = plan.saveContentsIds().getFirst();
            mergeMongoTxService.createMongoPart(request.content(), saveMongoId);
            return mergeMySqlTxService.createMySqlPart(
                    context,
                    request,
                    saveMongoId,
                    operationId
            );
        } catch (CustomException e) {
            log.warn("[SAGA] 머지용 브랜치/작업장 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw e;
        } catch (Exception e) {
            log.warn("[SAGA] 머지용 브랜치/작업장 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw new CustomException(CommitErrorCode.FAIL_MERGE);
        }
    }

    private void requestCompensation(String operationId, Exception cause) {
        try {
            compensationService.request(operationId, cause.getMessage());
        } catch (Exception compensationError) {
            log.error("[SAGA] 병합 생성 보상 요청 실패. Worker가 PENDING 작업을 복구합니다: operationId={}",
                    operationId, compensationError);
        }
    }
}
