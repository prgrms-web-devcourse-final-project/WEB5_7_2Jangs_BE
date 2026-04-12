package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SaveErrorCode implements ErrorCode {

    SAVE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 저장 데이터를 찾을 수 없습니다.", "SAVE_NOT_FOUND"),
    SAVE_NOT_OWNER(HttpStatus.BAD_REQUEST, "잘못된 접근입니다.", "SAVE_NOT_OWNER"),
    FAIL_TO_SAVE(HttpStatus.INTERNAL_SERVER_ERROR, "저장에 실패했습니다. 잠시후에 다시 시도해주세요.", "FAIL_TO_SAVE");
    private final HttpStatus status;
    private final String message;
    private final String error;

}
