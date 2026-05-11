package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SaveReader {

    private final SaveContentRepository saveContentRepository;
    private final SaveRepository saveRepository;

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
