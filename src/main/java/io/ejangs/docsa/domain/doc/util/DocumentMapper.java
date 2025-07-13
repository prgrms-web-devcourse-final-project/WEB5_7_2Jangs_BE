package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;

public class DocumentMapper {

    public static DocumentCreateResponse toCreateResponse(Doc doc) {
        return new DocumentCreateResponse(doc.getId());
    }

    public static DocumentTitleUpdateResponse toUpdateResponse(Doc doc) {
        return new DocumentTitleUpdateResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getUpdatedAt()
        );
    }
}
