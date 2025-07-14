package io.ejangs.docsa.domain.save.dao;

import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.dto.SaveBlock;
import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.domain.save.entity.Save;
import java.util.List;

public interface SaveRepositoryAdapter {

    Save findSaveById(Long id);

    SaveContent findSaveContentById(String id);

    SaveUpdateResponse updateSave(Save save, SaveContent saveContent, List<SaveBlock> content);
}
