package io.ejangs.docsa.domain.save.dao;

import com.mongodb.DuplicateKeyException;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SaveRepositoryAdapterImpl implements SaveRepositoryAdapter {

    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;

    @Override
    @Transactional(readOnly = true)
    public Save findSaveById(Long id) {
        return saveRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public SaveContent findSaveContentById(String id) {
        return saveContentRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    @Override
    @Transactional
    public SaveUpdateResponse updateSave(Save save, SaveContent saveContent,
            List<SaveBlock> content) {
        // TODO : 연관된 branch, doc의 updatedAt 도 수정해야 함.
        // MySQL 먼저 저장
        save.touch();
        saveRepository.save(save);

        // MongoDB 저장
        saveContent.updateContent(content);
        try {
            saveContentRepository.save(saveContent);
        } catch (DuplicateKeyException e) {
            log.warn("중복 키로 Mongo 저장 실패: {}", e.getMessage());
            throw new CustomException(SaveErrorCode.SAVE_CREATE_FAIL);
        } catch (DataAccessException e) {
            log.error("Mongo 저장 실패: {}", e.getMessage(), e);
            throw new CustomException(SaveErrorCode.SAVE_CREATE_FAIL);
        }

        return SaveMapper.toSaveUpdateResponse(save);
    }
}
