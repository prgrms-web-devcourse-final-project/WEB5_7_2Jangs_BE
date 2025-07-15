package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.save.dao.SaveRepositoryAdapter;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveService {

    private final DocRepository docRepository;
    private final UserRepository userRepository;
    private final SaveRepositoryAdapter saveRepository;

    @Transactional
    public SaveUpdateResponse updateSave(SaveUpdateIdDto dto, SaveUpdateRequest request) {
        userRepository.findById(dto.userId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        docRepository.findById(dto.documentId())
                .orElseThrow(() -> new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND));

        Save findSave = saveRepository.findSaveById(dto.saveId());

        // SAVE 소유자인지 검사
        if (!findSave.getBranch().getDoc().getUser().getId().equals(dto.userId())) {
            throw new CustomException(SaveErrorCode.SAVE_NOT_OWNER);
        }

        SaveContent saveContent = saveRepository.findSaveContentById(findSave.getSaveMongoId());

        return saveRepository.updateSave(findSave, saveContent, request.content());
    }
}
