package io.ejangs.docsa.domain.doc.app.create;

import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.app.SaveQueryService;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
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
public class DocCreateOrchestrator {

    private final SaveQueryService saveQueryService;
    private final DocCreateMySqlTxService docCreateMySqlTxService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    public DocCreateResponse create(String title, User user) {
        // 문서 생성에 경우 SaveContent 1개의 문서만 insert하여 단일 문서 트랜잭션은 보장되어 별도의 트랜잭션 처리 필요없음.
        // 다른 도메인에서는 다중 문서 트랜잭션을 위해 트랜잭션 설정 필요.
        SaveContent defaultSaveContent = saveQueryService.createSaveContent();
        String saveContentId = defaultSaveContent.getId();

        try {
            return docCreateMySqlTxService.createMySqlPart(title, user, saveContentId);
        } catch (Exception e) {
            log.warn("[SAGA] 문서 생성 실패 -> Mongo 삭제 Outbox 기록.", e);
            compensateMongo(saveContentId);
            throw new CustomException(DocErrorCode.FAIL_CREATE_DOCUMENT);
        }
    }

    private void compensateMongo(String saveContentId) {

        MongoIdsDto dto = new MongoIdsDto(List.of(saveContentId), null, null);
        mongoDeleteOutboxFactory.create(
                TriggerType.COMPENSATE,
                DomainType.DOC,
                OriginType.SAVE_CONTENT_ID,
                saveContentId,
                dto
        );
    }
}
