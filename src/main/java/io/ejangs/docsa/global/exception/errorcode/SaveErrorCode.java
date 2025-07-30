package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SaveErrorCode implements ErrorCode {

    SAVE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 저장 데이터를 찾을 수 없습니다.", "SAVE_NOT_FOUND"),
    SAVE_NOT_OWNER(HttpStatus.BAD_REQUEST, "잘못된 접근입니다.", "SAVE_NOT_OWNER"),
    CANNOT_DELETE_SAVE_WITH_NO_COMMIT
            (HttpStatus.BAD_REQUEST, "버전에 기록이 하나도 없는 경우, 저장을 삭제할 수 없습니다.",
                    "CANNOT_DELETE_SAVE_WITH_NO_COMMIT");
    private final HttpStatus status;
    private final String message;
    private final String error;

}
