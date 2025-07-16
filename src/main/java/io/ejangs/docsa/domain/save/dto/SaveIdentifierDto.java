package io.ejangs.docsa.domain.save.dto;

public record SaveIdentifierDto(Long documentId, Long saveId, Long userId) {

    public static SaveIdentifierDto of(Long documentId, Long saveId, Long userId) {
        return new SaveIdentifierDto(documentId, saveId, userId);
    }
}
