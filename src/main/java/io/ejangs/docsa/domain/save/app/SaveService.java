package io.ejangs.docsa.domain.save.app;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.doc.thumbnail.app.ThumbnailService;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailSyncResponse;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
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
    private final ThumbnailService thumbnailService;

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

        ThumbnailSyncResponse thumbnailSyncResponse = thumbnailService.requestUpdate(
                dto.userId(),
                dto.documentId()
        );

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

        return SaveMapper.toSaveUpdateResponse(findSave.getUpdatedAt(), thumbnailSyncResponse);
    }

    private Save getValidSave(SaveIdentifierDto dto) {

        Save findSave = saveQueryService.getSaveById(dto.saveId());

        saveQueryService.checkSaveAndDocOwner(findSave, dto.userId(), dto.documentId());
        return findSave;
    }


}
