package io.ejangs.docsa.domain.commit.app.create;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.commit.util.CommitMapper;
import io.ejangs.docsa.domain.doc.entity.Doc;
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
public class CommitCreateOrchestrator {

    private final CommitMySqlTxService commitMySqlTxService;
    private final CommitMongoTxService commitMongoTxService;
    private final MongoCreateOperationService operationService;
    private final MongoCreateCompensationService compensationService;

    public CreateCommitResponse create(
            CreateCommitRequest request,
            String baseCommitCbsMongoId,
            Doc doc,
            Branch branch,
            Long userId,
            String operationId,
            String requestHash,
            MongoIdsDto plan
    ) {
        MongoCreateOperationStart start = operationService.start(
                operationId, userId, MongoCreateOperationType.COMMIT, requestHash, plan
        );
        if (!start.started()) {
            return new CreateCommitResponse(start.resultEntityId());
        }

        try {
            commitMongoTxService.createMongoPart(request, baseCommitCbsMongoId, plan);
            Commit commit = commitMySqlTxService.createMySqlPart(
                    doc,
                    branch,
                    request,
                    plan.commitBlockSequenceIds().getFirst(),
                    operationId
            );
            return CommitMapper.toCreateCommitResponse(commit);
        } catch (CustomException e) {
            log.warn("[SAGA] 커밋 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw e;
        } catch (Exception e) {
            log.warn("[SAGA] 커밋 생성 실패 -> 생성 작업 보상 요청.", e);
            requestCompensation(operationId, e);
            throw new CustomException(CommitErrorCode.FAIL_CREATE_COMMIT);
        }
    }

    private void requestCompensation(String operationId, Exception cause) {
        try {
            compensationService.request(operationId, cause.getMessage());
        } catch (Exception compensationError) {
            log.error("[SAGA] 커밋 생성 보상 요청 실패. Worker가 PENDING 작업을 복구합니다: operationId={}",
                    operationId, compensationError);
        }
    }
}
