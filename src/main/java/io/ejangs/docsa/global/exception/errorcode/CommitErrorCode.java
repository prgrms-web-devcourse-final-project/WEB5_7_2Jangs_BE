package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommitErrorCode implements ErrorCode {

    COMMIT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다.", "COMMIT_NOT_FOUND"),
    COMMIT_BAD_REQUEST(HttpStatus.BAD_REQUEST, "변경 사항이 없습니다.", "COMMIT_BAD_REQUEST"),

    ;

    private final HttpStatus status;
    private final String message;
    private final String error;

}

