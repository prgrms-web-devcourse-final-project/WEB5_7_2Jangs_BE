package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.document.dao.DocumentRepository;
import io.ejangs.docsa.domain.save.dao.SaveRepository;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveService {

    private final SaveRepository saveRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public LocalDateTime updateSave(SaveUpdateIdDto dto, SaveUpdateRequest request) {
        userRepository.findById(dto.userId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        documentRepository.findById(dto.documentId())
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        Save findSave = saveRepository.findById(dto.saveId())
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        findSave.updateContent(request.content());
        saveRepository.flush(); // flush 을 통해 updatedAt 필드를 갱신해야 하므로 명시적으로 flush
        return findSave.getUpdatedAt();
    }
}
