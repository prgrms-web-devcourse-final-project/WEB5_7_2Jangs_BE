package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BranchErrorCode implements ErrorCode {

    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 브랜치를 찾을 수 없습니다.", "BRANCH_NOT_FOUND"),
    ;

    private final HttpStatus status;
    private final String message;
    private final String error;

}
