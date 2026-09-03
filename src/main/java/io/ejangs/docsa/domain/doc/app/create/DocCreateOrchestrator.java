package io.ejangs.docsa.domain.doc.app.create;

import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.app.SaveWriter;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
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
public class DocCreateOrchestrator {

    private final SaveWriter saveWriter;
    private final DocCreateMySqlTxService docCreateMySqlTxService;
    private final MongoCreateOperationService operationService;
    private final MongoCreateCompensationService compensationService;

    public DocCreateResponse create(
            String title,
            User user,
            String operationId,
            String requestHash,
            MongoIdsDto plan
    ) {
        MongoCreateOperationStart start = operationService.start(
                operationId,
                user.getId(),
                MongoCreateOperationType.DOC,
                requestHash,
                plan
        );
        if (!start.started()) {
            return new DocCreateResponse(start.resultEntityId(), start.resultSaveId());
        }

        try {
            String saveContentId = plan.saveContentsIds().getFirst();
            saveWriter.insertSaveContent(saveContentId);
            return docCreateMySqlTxService.createMySqlPart(
                    title, user, saveContentId, operationId
            );
        } catch (CustomException e) {
            requestCompensation(operationId, e);
            throw e;
        } catch (Exception e) {
            requestCompensation(operationId, e);
            throw new CustomException(DocErrorCode.FAIL_CREATE_DOCUMENT);
        }
    }

    private void requestCompensation(String operationId, Exception cause) {
        log.warn("[SAGA] 문서 생성 실패 -> 생성 작업 보상 요청.", cause);
        try {
            compensationService.request(operationId, cause.getMessage());
        } catch (Exception compensationError) {
            log.error("[SAGA] 문서 생성 보상 요청 실패. Worker가 PENDING 작업을 복구합니다: operationId={}",
                    operationId, compensationError);
        }
    }
}
