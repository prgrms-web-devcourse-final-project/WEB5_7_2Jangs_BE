package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommitErrorCode implements ErrorCode {

    COMMIT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다.", "COMMIT_NOT_FOUND"),
    MONGO_COMMIT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다", "MONGO_COMMIT_NOT_FOUND"),
    COMMIT_BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", "COMMIT_BAD_REQUEST"),
    INVALID_FROM_COMMIT(HttpStatus.BAD_REQUEST, "요청이 잘못되었습니다.", " INVALID_FROM_COMMIT"),
    CAN_NOT_DELETE_COMMIT(HttpStatus.BAD_REQUEST, "이 기록은 삭제할 수 없습니다.", "CAN_NOT_DELETE_COMMIT"),
    IS_NOT_LEAF_COMMIT(HttpStatus.BAD_REQUEST, "브랜치의 마지막 기록이 아닙니다.", "IS_NOT_LEAF_COMMIT"),
    FAIL_CREATE_COMMIT(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류로 인해 기록 생성에 실패했습니다.",
            "FAIL_CREATE_COMMIT"),
    FAIL_DELETE_COMMIT(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류로 인해 기록 삭제에 실패했습니다.",
            "FAIL_DELETE_COMMIT"),
    FAIL_SAVE_MONGODB(HttpStatus.INTERNAL_SERVER_ERROR, "MongoDB 저장에 문제가 생겼습니다.",
            "FAIL_SAVE_MONGODB");

    private final HttpStatus status;
    private final String message;
    private final String error;

}

