package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BranchErrorCode implements ErrorCode {

    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 분기를 찾을 수 없습니다.", "BRANCH_NOT_FOUND"),
    BRANCH_NOT_FOUND_OR_FORBIDDEN(HttpStatus.NOT_FOUND, "해당 분기를 찾을 수 없습니다.", "BRANCH_NOT_FOUND_OR_FORBIDDEN"),
    MAIN_BRANCH_FIX_UNAVAILABLE(HttpStatus.BAD_REQUEST, "Main 분기는 삭제 또는 수정할 수 없습니다.", "MAIN_BRANCH_FIX_UNAVAILABLE"),
    SUB_BRANCH_DELETE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "서브 분기가 있어 해당 버전을 삭제할 수 없습니다.", "SUB_BRANCH_DELETE_UNAVAILABLE"),
    BRANCH_DELETE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "해당 분기를 삭제할 수 없습니다.", "BRANCH_DELETE_UNAVAILABLE"),
    BRANCH_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, "새로운 분기의 이름은 다른 버전의 이름과 중복될 수 없습니다.",
            "BRANCH_NAME_DUPLICATED"),
    FAIL_CREATE_BRANCH(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류로 인해 분기 생성에 실패했습니다.",
            "FAIL_CREATE_BRANCH");
    private final HttpStatus status;
    private final String message;
    private final String error;

}
