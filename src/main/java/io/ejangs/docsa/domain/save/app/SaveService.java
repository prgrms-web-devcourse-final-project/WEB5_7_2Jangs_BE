package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocumentRepository;
import io.ejangs.docsa.domain.save.dao.SaveRepositoryAdapter;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SaveRepositoryAdapter saveRepository;

    @Transactional
    public SaveUpdateResponse updateSave(SaveUpdateIdDto dto, SaveUpdateRequest request) {
        userRepository.findById(dto.userId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        documentRepository.findById(dto.documentId())
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        Save findSave = saveRepository.findSaveById(dto.saveId());

        // 이런 예외가 있나?
        SaveContent saveContent = saveRepository.findSaveContentById(findSave.getSaveMongoId());

        return saveRepository.updateSave(findSave, saveContent, request.content());
    }
}
