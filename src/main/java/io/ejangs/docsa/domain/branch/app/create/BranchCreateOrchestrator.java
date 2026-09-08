package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
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
public class BranchCreateOrchestrator {

    private final BranchCreateMongoTxService branchCreateMongoTxService;
    private final BranchCreateMySqlTxService branchCreateMySqlTxService;
    private final MongoCreateOperationService operationService;
    private final MongoCreateCompensationService compensationService;

    public BranchCreateResponse create(
            BranchCreateContext context,
            Long userId,
            String operationId,
            String requestHash,
            MongoIdsDto plan
    ) {
        MongoCreateOperationStart start = operationService.start(
                operationId, userId, MongoCreateOperationType.BRANCH, requestHash, plan
        );
        if (!start.started()) {
            return new BranchCreateResponse(start.resultEntityId(), start.resultSaveId());
        }

        try {
            String saveContentId = plan.saveContentsIds().getFirst();
            branchCreateMongoTxService.createSaveContentFromCommit(
                    context.fromCommitMongoId(), saveContentId
            );
            return branchCreateMySqlTxService.createMySqlPart(context, saveContentId, operationId);
        } catch (CustomException e) {
            log.warn("[SAGA] 브랜치/저장 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw e;
        } catch (Exception e) {
            log.warn("[SAGA] 브랜치/저장 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw new CustomException(BranchErrorCode.FAIL_CREATE_BRANCH);
        }
    }

    private void requestCompensation(String operationId, Exception cause) {
        try {
            compensationService.request(operationId, cause.getMessage());
        } catch (Exception compensationError) {
            log.error("[SAGA] 브랜치 생성 보상 요청 실패. Worker가 PENDING 작업을 복구합니다: operationId={}",
                    operationId, compensationError);
        }
    }
}
