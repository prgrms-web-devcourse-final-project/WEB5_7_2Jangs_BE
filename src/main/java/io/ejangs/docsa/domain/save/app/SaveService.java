package io.ejangs.docsa.domain.save.app;

import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocumentErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveService {

    private final SaveRepository saveRepository;
    private final DocRepository docRepository;
    private final UserRepository userRepository;

    @Transactional
    public SaveUpdateResponse updateSave(SaveUpdateIdDto dto, SaveUpdateRequest request) {
        userRepository.findById(dto.userId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        docRepository.findById(dto.documentId())
                .orElseThrow(() -> new CustomException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        Save findSave = saveRepository.findById(dto.saveId())
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        return null;
    }
}
