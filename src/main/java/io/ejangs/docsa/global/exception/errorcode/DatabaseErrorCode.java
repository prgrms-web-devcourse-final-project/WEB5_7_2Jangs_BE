package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DatabaseErrorCode implements ErrorCode {
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
            "DATABASE_ERROR");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
