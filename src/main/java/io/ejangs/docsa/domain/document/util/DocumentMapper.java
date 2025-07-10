package io.ejangs.docsa.domain.document.util;

import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.entity.Document;

public class DocumentMapper {

    public static DocumentCreateResponse toCreateResponse(Document document) {
        return new DocumentCreateResponse(document.getId());
    }



}
