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

@Repository
@RequiredArgsConstructor
public class SaveRepositoryAdapterImpl implements SaveRepositoryAdapter {

    private final SaveRepository saveRepository;
    private final SaveContentRepository saveContentRepository;

    @Override
    public Save findSaveById(Long id) {
        return saveRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    @Override
    public SaveContent findSaveContentById(String id) {
        return saveContentRepository.findById(id)
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));
    }

    @Override
    public SaveUpdateResponse updateSave(Save save, SaveContent saveContent, String content) {
        Map<String, Object> json = JsonConverter.toMap(content);

        saveContent.updateContent(json);
        save.touch(); // jpa 는 변경감지가 된다.
        saveContentRepository.save(saveContent); // jpa를 사용하지 않으니 변경감지 안된다.

        return SaveMapper.toSaveUpdateResponse(save);
    }
}
