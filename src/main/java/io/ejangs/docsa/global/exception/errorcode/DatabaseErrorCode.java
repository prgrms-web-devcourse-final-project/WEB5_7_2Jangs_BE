package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DatabaseErrorCode implements ErrorCode {

    FAIL_TO_SAVE(HttpStatus.INTERNAL_SERVER_ERROR, "저장에 실패했습니다", "FAIL_TO_SAVE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
