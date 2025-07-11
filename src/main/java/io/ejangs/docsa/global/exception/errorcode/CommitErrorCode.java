package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommitErrorCode implements ErrorCode {

    COMMIT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다.", "COMMIT_NOT_FOUND"),
    ;

    private final HttpStatus status;
    private final String message;
    private final String error;

}
