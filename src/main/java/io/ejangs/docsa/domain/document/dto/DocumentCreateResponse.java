package io.ejangs.docsa.domain.document.dto;

import io.ejangs.docsa.domain.document.entity.Document;

public record DocumentCreateResponse(
    Long id
) {

    public static DocumentCreateResponse of(Document document) {
        return new DocumentCreateResponse(document.getId());
    }
}
