package io.ejangs.docsa.domain.doc.app;

import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.mongo.deletion.app.MongoDeleteRetryService;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocCreateSagaService {

    private final SaveContentRepository saveContentRepository;
    private final DocCreateMySqlTxService docCreateMySqlTxService;
    private final MongoDeleteRetryService mongoDeleteRetryService;

    public DocCreateResponse create(DocTitleRequest request, Long userId) {
        // 문서 생성에 경우 SaveContent 1개의 문서만 insert하여 단일 문서 트랜잭션은 보장되어 별도의 트랜잭션 처리 필요없음.
        // 다른 도메인에서는 다중 문서 트랜잭션을 위해 트랜잭션 설정 필요.
        SaveContent defaultSaveContent = createDefaultSaveContent();
        String saveContentId = defaultSaveContent.getId();

        try {
            return docCreateMySqlTxService.createMySqlPart(request, userId, saveContentId);
        } catch (Exception e) {
            log.warn("[SAGA] MYSQL 생성 실패 -> Mongo 보상 삭제. saveContentId = {}", saveContentId, e);
            compensateMongo(saveContentId);
            throw e;
        }
    }

    private void compensateMongo(String saveContentId) {
        // 기존 삭제 파이프라인 재사용: @Retryable + @Recover(+ Failure 저장)
        MongoIdsDto dto = MongoIdsDto.forSaveContent(saveContentId);
        mongoDeleteRetryService.deleteMongoData(dto);
    }

    // save로 이동?
    private SaveContent createDefaultSaveContent() {
        try {
            return saveContentRepository.save(SaveContent.builder().build());
        } catch (DataAccessException e) {
            log.error("DefaultSaveContent Mongo 저장 실패 - {}", e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.DATABASE_ERROR);
        } catch (Exception e) {
            log.error("DefaultSaveContent Mongo 알 수 없는 오류 - {}", e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.DATABASE_ERROR);
        }
    }
}
