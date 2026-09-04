package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CreateOperationErrorCode implements ErrorCode {

    IN_PROGRESS(HttpStatus.CONFLICT, "같은 생성 요청이 처리 중입니다.", "CREATE_OPERATION_IN_PROGRESS"),
    CANCELLED(HttpStatus.CONFLICT, "취소된 생성 요청입니다. 새 Idempotency-Key로 다시 요청해주세요.",
            "CREATE_OPERATION_CANCELLED"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT,
            "Idempotency-Key가 다른 생성 요청에 이미 사용되었습니다.",
            "IDEMPOTENCY_KEY_REUSED"),
    INVALID_STATE(HttpStatus.CONFLICT, "생성 작업 상태가 변경되어 요청을 완료할 수 없습니다.",
            "CREATE_OPERATION_INVALID_STATE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
