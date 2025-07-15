package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SaveErrorCode implements ErrorCode {

    SAVE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 저장 데이터를 찾을 수 없습니다.", "SAVE_NOT_FOUND"),
    SAVE_CREATE_FAIL(HttpStatus.BAD_REQUEST, "저장에 실패했습니다.", "SAVE_CREATE_FAIL"),
    SAVE_NOT_OWNER(HttpStatus.BAD_REQUEST, "잘못된 접근입니다.", "SAVE_NOT_OWNER"),
    FAILED_TO_SAVE_IN_MONGO(HttpStatus.INTERNAL_SERVER_ERROR, "저장을 실패했습니다.", "SAVE_CREATE_FAIL"),
    SAVE_NOT_IN_DOCUMENT(HttpStatus.BAD_REQUEST, "잘못된 접근입니다.", "SAVE_NOT_IN_DOCUMENT");

    private final HttpStatus status;
    private final String message;
    private final String error;

}
