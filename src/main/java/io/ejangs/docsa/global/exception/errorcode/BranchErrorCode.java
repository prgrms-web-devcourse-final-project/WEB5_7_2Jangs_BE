package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BranchErrorCode implements ErrorCode {

    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 버전을 찾을 수 없습니다.", "BRANCH_NOT_FOUND"),
    BRANCH_NOT_FOUND_OR_FORBIDDEN(HttpStatus.NOT_FOUND, "해당 버전을 찾을 수 없습니다", "BRANCH_NOT_FOUND_OR_FORBIDDEN"),
    MAIN_BRANCH_DELETE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "메인 버전을 삭제할 수 없습니다", "MAIN_BRANCH_DELETE_UNAVAILABLE"),
    SUB_BRANCH_DELETE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "서브 버전이 있어 해당 브랜치를 삭제할 수 없습니다", "SUB_BRANCH_DELETE_UNAVAILABLE"),
    BRANCH_DELETE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "해당 버전을 삭제할 수 없습니다", "BRANCH_DELETE_UNAVAILABLE");

    private final HttpStatus status;
    private final String message;
    private final String error;

}
