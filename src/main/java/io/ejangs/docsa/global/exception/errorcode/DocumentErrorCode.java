package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문서를 찾을 수 없습니다.", "DOCUMENT_NOT_FOUND");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
