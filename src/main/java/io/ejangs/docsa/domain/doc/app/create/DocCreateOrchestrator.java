package io.ejangs.docsa.domain.doc.app.create;

import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.app.SaveQueryService;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteRetryService;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
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
    private final MongoDeleteRetryService mongoDeleteRetryService;

    public DocCreateResponse create(String title, User user) {
        // 문서 생성에 경우 SaveContent 1개의 문서만 insert하여 단일 문서 트랜잭션은 보장되어 별도의 트랜잭션 처리 필요없음.
        // 다른 도메인에서는 다중 문서 트랜잭션을 위해 트랜잭션 설정 필요.
        SaveContent defaultSaveContent = saveQueryService.createSaveContent();
        String saveContentId = defaultSaveContent.getId();

        try {
            return docCreateMySqlTxService.createMySqlPart(title, user, saveContentId);
        } catch (Exception e) {
            log.warn("[SAGA] 문서 생성 실패 -> Mongo 보상 삭제.", e);
            compensateMongo(saveContentId);
            throw e;
        }
    }

    private void compensateMongo(String saveContentId) {
        // 기존 삭제 파이프라인 재사용: @Retryable + @Recover(+ Failure 저장)
        MongoIdsDto dto = new MongoIdsDto(List.of(saveContentId),null,null);
        mongoDeleteRetryService.deleteMongoData(dto);
    }
}
