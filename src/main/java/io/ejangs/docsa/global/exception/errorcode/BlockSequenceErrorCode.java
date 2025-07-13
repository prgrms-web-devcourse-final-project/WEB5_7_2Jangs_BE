package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BlockSequenceErrorCode implements ErrorCode {

    // TODO: 뭔가 뭔가임
    BLOCK_SEQUENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "문단의 순서를 찾을 수 없습니다.", "BLOCK_SEQUENCE_NOT_FOUND"),
    BLOCK_SEQUENCE_INVALID(HttpStatus.BAD_REQUEST, "문단의 순서가 유효하지 않습니다.", "BLOCK_SEQUENCE_INVALID")
    ;

    private final HttpStatus status;
    private final String message;
    private final String error;

}

