package io.ejangs.docsa.domain.save.dto;

public record SaveGetIdDto(Long documentId, Long saveId, Long userId) {

    public static SaveGetIdDto of(Long documentId, Long saveId, Long userId) {
        return new SaveGetIdDto(documentId, saveId, userId);
    }
}
