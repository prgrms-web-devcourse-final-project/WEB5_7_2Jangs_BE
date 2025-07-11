package io.ejangs.docsa.domain.save.dto;

public record SaveUpdateIdDto(Long documentId, Long saveId, Long userId) {

    public static SaveUpdateIdDto of(Long documentId, Long saveId, Long userId) {
        return new SaveUpdateIdDto(documentId, saveId, userId);
    }
}
