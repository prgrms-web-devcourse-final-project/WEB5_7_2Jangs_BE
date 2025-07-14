package io.ejangs.docsa.domain.save.dao;

import io.ejangs.docsa.domain.branch.util.JsonConverter;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.dao.mysql.SaveRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.domain.save.util.SaveMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
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
    public SaveUpdateResponse updateSave(Save save, SaveContent saveContent, String content) {
        Map<String, Object> json = JsonConverter.toMap(content);

        // MySQL 먼저 저장
        save.touch();
        saveRepository.save(save);

        // MongoDB 저장
        saveContent.updateContent(json);
        saveContentRepository.save(saveContent);

        return SaveMapper.toSaveUpdateResponse(save);
    }
}
