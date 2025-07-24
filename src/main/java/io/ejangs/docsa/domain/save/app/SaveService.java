package io.ejangs.docsa.domain.save.app;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveIdentifierDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.util.RenewUpdatedAtHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveService {

    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;

    @Transactional(readOnly = true)
    public SaveGetResponse getSave(SaveIdentifierDto dto) {
        Save findSave = getValidSave(dto);

        SaveContent saveContent = getSaveContentById(findSave.getSaveMongoId());

        return SaveMapper.toSaveGetResponse(findSave.getUpdatedAt(), saveContent.getContent());
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveUpdateResponse updateSave(SaveIdentifierDto dto, SaveUpdateRequest request) {
        Save findSave = getValidSave(dto);

        SaveContent saveContent = getSaveContentById(findSave.getSaveMongoId());

        // MySQL 먼저 저장
        RenewUpdatedAtHelper.touch(findSave);
        saveRepository.save(findSave);

        // MongoDB 저장
        saveContent.updateContent(request.content());
        try {
            saveContentRepository.save(saveContent);
        } catch (DataAccessException e) {
            log.error("[Mongo 저장 실패 - DataAccess] saveId={}, message={}", findSave.getId(), e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.MONGO_ERROR);
        } catch (Exception e) {
            log.error("[Mongo 저장 실패 - Unknown] saveId={}, message={}", findSave.getId(), e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.MONGO_ERROR);
        }

        return SaveMapper.toSaveUpdateResponse(findSave.getUpdatedAt());
    }

    @Transactional
    public void deleteSave(SaveIdentifierDto dto) {
        Save findSave = getValidSave(dto);
        Branch branch = findSave.getBranch();

        // 해당 브랜치에 커밋이 하나도 없고 저장만 존재하는 최초 상태에서는 저장을 삭제할 수 없다.
        if (branch.getCommits().isEmpty()) {
            throw new CustomException(SaveErrorCode.CANNOT_DELETE_SAVE_WITH_NO_COMMIT);
        }

        RenewUpdatedAtHelper.touch(findSave);
        saveRepository.delete(findSave);
        try {
            saveContentRepository.deleteById(findSave.getSaveMongoId());
        } catch (DataAccessException e) {
            log.error("[Mongo 삭제 실패 - DataAccess] saveId={}, message={}", findSave.getId(), e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.MONGO_ERROR);
        } catch (Exception e) {
            log.error("[Mongo 삭제 실패 - Unknown] saveId={}, message={}", findSave.getId(), e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.MONGO_ERROR);
        }
    }

    public Save getSaveById(Long id) {
        return saveRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    public SaveContent getSaveContentById(String id) {
        return saveContentRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    public void deleteSaveIfExists(Long branchId) {
        saveRepository.findByBranchId(branchId).ifPresent(save -> {
            saveRepository.delete(save);
            saveContentRepository.findById(save.getSaveMongoId())
                    .ifPresent(saveContentRepository::delete);
        });
    }

    private Save getValidSave(SaveIdentifierDto dto) {
        // 존재하는 user, document 인지 검사
        Save findSave = getSaveById(dto.saveId());

        // SAVE 소유자인지 검사
        checkSaveAndDocOwner(findSave, dto.userId(), dto.documentId());
        return findSave;
    }

    private void checkSaveAndDocOwner(Save save, Long userId, Long documentId) {
        if (!saveRepository.validateSaveOwnership(save.getId(), documentId, userId)) {
            throw new CustomException(SaveErrorCode.SAVE_NOT_OWNER);
        }
    }
}
