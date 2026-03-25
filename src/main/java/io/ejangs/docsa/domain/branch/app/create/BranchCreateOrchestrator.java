package io.ejangs.docsa.domain.branch.app.create;

import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.global.mongo.outbox.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.outbox.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.outbox.app.MongoDeleteOutboxFactory;
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

    public BranchCreateResponse createBranchOrSave(BranchCreateContext context) {
        String saveContentId = branchCreateMongoTxService.createSaveContentFromCommit(
                context.fromCommitMongoId());

        try {
            return branchCreateMySqlTxService.createBranchOrSave(context, saveContentId);
        } catch (Exception e) {
            log.warn("[SAGA] 브랜치/저장 생성 실패 -> Mongo 삭제 Outbox 기록.", e);
            MongoIdsDto compensateTarget = new MongoIdsDto(List.of(saveContentId), null, null);
            mongoDeleteOutboxFactory.create(
                    TriggerType.COMPENSATE,
                    DomainType.BRANCH,
                    OriginType.SAVE_CONTENT_ID,
                    saveContentId,
                    compensateTarget
            );
            throw e;
        }
    }
}
