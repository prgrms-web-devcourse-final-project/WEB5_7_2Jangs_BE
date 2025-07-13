package io.ejangs.docsa.domain.save.dao;

import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;

public interface SaveRepositoryAdapter {

    Save findSaveById(Long id);

    SaveContent findSaveContentById(String id);

    SaveUpdateResponse updateSave(Save save, SaveContent saveContent, String content);
}
