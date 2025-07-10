package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문서를 찾을 수 없습니다.", "DOCUMENT_NOT_FOUND"),
    JSON_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 직렬화에 실패했습니다.", "JSON_SERIALIZATION_FAILED");


    private final HttpStatus status;
    private final String message;
    private final String error;
}
