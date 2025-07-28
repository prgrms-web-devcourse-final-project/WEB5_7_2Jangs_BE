package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다.", "DUPLICATE_EMAIL"),
    INVALID_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다.", "INVALID_CODE"),
    EXPIRED_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다.", "EXPIRED_CODE"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.", "INVALID_CREDENTIALS"),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", "LOGIN_REQUIRED"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.", "AUTHENTICATION_FAILED"),
    ALREADY_REGISTERED_USER(HttpStatus.BAD_REQUEST, "이미 가입한 사용자입니다.", "ALREADY_REGISTERED_USER"),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "기존 비밀번호와 동일한 비밀번호입니다.", "SAME_AS_OLD_PASSWORD"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "로그인 상태에서는 사용할 수 없습니다.", "ACCESS_DENIED"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", "INTERNAL_ERROR"),
    UNSUPPORTED_CODE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 코드 타입입니다.", "UNSUPPORTED_CODE_TYPE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
