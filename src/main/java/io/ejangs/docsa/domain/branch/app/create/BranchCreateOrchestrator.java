package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.dto.BranchCreateContext;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.BranchErrorCode;
import io.ejangs.docsa.global.outbox.mongo.dto.MongoIdsDto;
import io.ejangs.docsa.global.outbox.mongo.app.MongoDeleteOutboxFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchCreateOrchestrator {

    private final BranchCreateMongoTxService branchCreateMongoTxService;
    private final BranchCreateMySqlTxService branchCreateMySqlTxService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public BranchCreateResponse create(BranchCreateContext context) {
        String saveContentId = branchCreateMongoTxService.createSaveContentFromCommit(
                context.fromCommitMongoId());

        try {
            return branchCreateMySqlTxService.createMySqlPart(context, saveContentId);
        } catch (Exception e) {
            log.warn("[SAGA] 브랜치/저장 생성 실패 -> Mongo 삭제 Outbox 기록.", e);
            MongoIdsDto compensateTarget = new MongoIdsDto(List.of(saveContentId), null, null);
            mongoDeleteOutboxFactory.createBranchCreateCompensation(saveContentId, compensateTarget);
            throw new CustomException(BranchErrorCode.FAIL_CREATE_BRANCH);
        }
    }
}
