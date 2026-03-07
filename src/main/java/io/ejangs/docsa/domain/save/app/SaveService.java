package io.ejangs.docsa.domain.save.app;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.mongo.deletion.dto.MongoIdsDto;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.DomainType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.OriginType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutbox.TriggerType;
import io.ejangs.docsa.global.mongo.deletion.entity.MongoDeleteOutboxFactory;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class SaveService {

    private final SaveQueryService saveQueryService;
    private final MongoDeleteOutboxFactory mongoDeleteOutboxFactory;

    @Transactional(readOnly = true)
    public SaveGetResponse getSave(SaveIdentifierDto dto) {
        Save findSave = getValidSave(dto);

        SaveContent saveContent = saveQueryService.getSaveContentById(findSave.getSaveMongoId());

        return SaveMapper.toSaveGetResponse(findSave.getUpdatedAt(), saveContent.getContent());
    }

    public SaveUpdateResponse updateSave(SaveIdentifierDto dto, SaveUpdateRequest request) {
        Save findSave = getValidSave(dto);

        // MySQL 먼저 저장
        RenewUpdatedAtHelper.touch(findSave);
        saveQueryService.saveSave(findSave);

        // MongoDB 저장
        try {
            SaveContent saveContent = saveQueryService.getSaveContentById(findSave.getSaveMongoId());
            saveContent.updateContent(request.content());
            saveQueryService.saveSaveContent(saveContent);
        } catch (DuplicateKeyException e) {
            log.warn("중복 키로 Mongo 저장 실패 - saveId={}, mongoId={}, message={}", findSave.getId(),
                    findSave.getSaveMongoId(), e.getMessage());
            throw new CustomException(SaveErrorCode.FAIL_TO_SAVE);
        } catch (DataAccessException e) {
            log.error("Mongo 저장 실패: {}", e.getMessage(), e);
            throw new CustomException(SaveErrorCode.FAIL_TO_SAVE);
        }

        return SaveMapper.toSaveUpdateResponse(findSave.getUpdatedAt());
    }

    public void deleteSave(SaveIdentifierDto dto) {
        Save findSave = getValidSave(dto);
        Branch branch = findSave.getBranch();
        String saveMongoId = findSave.getSaveMongoId();

        // 해당 브랜치에 커밋이 하나도 없고 저장만 존재하는 최초 상태에서는 저장을 삭제할 수 없다.
        if (branch.getCommits().isEmpty()) {
            throw new CustomException(SaveErrorCode.CANNOT_DELETE_SAVE_WITH_NO_COMMIT);
        }

        RenewUpdatedAtHelper.touch(findSave);
        branch.removeSave();
        saveQueryService.deleteSave(findSave);

        MongoIdsDto outboxTarget = new MongoIdsDto(
                saveMongoId == null || saveMongoId.isBlank() ? List.of() : List.of(saveMongoId),
                null,
                null
        );
        mongoDeleteOutboxFactory.create(
                TriggerType.DELETE,
                DomainType.SAVE,
                OriginType.SAVE_ID,
                findSave.getId(),
                outboxTarget
        );
    }

    private Save getValidSave(SaveIdentifierDto dto) {
        // 존재하는 user, document 인지 검사
        Save findSave = saveQueryService.getSaveById(dto.saveId());

        // SAVE 소유자인지 검사
        saveQueryService.checkSaveAndDocOwner(findSave, dto.userId(), dto.documentId());
        return findSave;
    }


}
