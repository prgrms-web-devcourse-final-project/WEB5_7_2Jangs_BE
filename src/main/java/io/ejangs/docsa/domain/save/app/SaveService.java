package io.ejangs.docsa.domain.save.app;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.doc.dao.mysql.DocRepository;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveGetIdDto;
import io.ejangs.docsa.domain.save.dto.SaveUpdateIdDto;
import io.ejangs.docsa.domain.save.dto.request.SaveUpdateRequest;
import io.ejangs.docsa.domain.save.dto.response.SaveGetResponse;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.DocErrorCode;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
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

    private final DocRepository docRepository;
    private final UserRepository userRepository;
    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;

    @Transactional(readOnly = true)
    public SaveGetResponse getSave(SaveGetIdDto dto) {
        // 존재하는 user, document 인지 검사
        checkValidation(dto.userId(), dto.documentId());

        Save findSave = getSaveById(dto.saveId());

        // SAVE 소유자인지 검사
        checkSaveOwner(findSave, dto.userId());

        SaveContent saveContent = getSaveContentById(findSave.getSaveMongoId());

        return SaveMapper.toSaveGetResponse(findSave.getUpdatedAt(), saveContent.getContent());
    }

    @Transactional
    public SaveUpdateResponse updateSave(SaveUpdateIdDto dto, SaveUpdateRequest request) {
        // 존재하는 user, document 인지 검사
        checkValidation(dto.userId(), dto.documentId());

        Save findSave = getSaveById(dto.saveId());

        // SAVE 소유자인지 검사
        checkSaveOwner(findSave, dto.userId());

        SaveContent saveContent = getSaveContentById(findSave.getSaveMongoId());

        // MySQL 먼저 저장
        RenewUpdatedAtHelper.touch(findSave);
        saveRepository.save(findSave);

        // MongoDB 저장
        saveContent.updateContent(request.content());
        try {
            saveContentRepository.save(saveContent);
        } catch (DuplicateKeyException e) {
            log.warn("중복 키로 Mongo 저장 실패: {}", e.getMessage());
            throw new CustomException(SaveErrorCode.SAVE_CREATE_FAIL);
        } catch (DataAccessException e) {
            log.error("Mongo 저장 실패: {}", e.getMessage(), e);
            throw new CustomException(SaveErrorCode.FAILED_TO_SAVE_IN_MONGO);
        }

        return SaveMapper.toSaveUpdateResponse(findSave);
    }

    public Save getSaveById(Long id) {
        return saveRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    public SaveContent getSaveContentById(String id) {
        return saveContentRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    private void checkValidation(Long userId, Long documentId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!docRepository.existsById(documentId)) {
            throw new CustomException(DocErrorCode.DOCUMENT_NOT_FOUND);
        }
    }

    private void checkSaveOwner(Save save, Long userId) {
        if (!save.getBranch().getDoc().getUser().getId().equals(userId)) {
            throw new CustomException(SaveErrorCode.SAVE_NOT_OWNER);
        }
    }

    public void deleteSaveIfExists(Long branchId) {
        saveRepository.findByBranchId(branchId).ifPresent(save -> {
            saveRepository.delete(save);
            saveContentRepository.findById(save.getSaveMongoId())
                    .ifPresent(saveContentRepository::delete);
        });
    }
}
