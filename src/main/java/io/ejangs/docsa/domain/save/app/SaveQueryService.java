package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveQueryService {

    private final SaveContentRepository saveContentRepository;
    private final SaveRepository saveRepository;

    public SaveContent createSaveContent() {
        try {
            return saveContentRepository.save(SaveContent.builder().build());
        } catch (Exception e) {
            log.error("[Mongo] DefaultSaveContent 생성 실패 - {}", e.getMessage(), e);
            throw new CustomException(DatabaseErrorCode.DATABASE_ERROR);
        }
    }

    public Save createSave(Branch branch, String mongoID) {
        return saveRepository.save(Save.builder().branch(branch).saveMongoId(mongoID).build());
    }

    public Save saveSave(Save save) {
        return saveRepository.save(save);
    }

    public SaveContent saveSaveContent(SaveContent saveContent) {
        return saveContentRepository.save(saveContent);
    }

    public Save getSaveById(Long id) {
        return saveRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

    }

    public SaveContent getSaveContentById(String id) {
        return saveContentRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    public void checkSaveAndDocOwner(Save save, Long userId, Long documentId) {
        if (!saveRepository.validateSaveOwnership(save.getId(), documentId, userId)) {
            throw new CustomException(SaveErrorCode.SAVE_NOT_OWNER);
        }
    }

}
