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
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, "세션이 유효하지 않습니다. 다시 로그인해주세요.", "INVALID_SESSION"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.", "AUTHENTICATION_FAILED"),
    ALREADY_REGISTERED_USER(HttpStatus.BAD_REQUEST, "이미 가입한 사용자입니다.", "ALREADY_REGISTERED_USER"),
    USER_NOT_FOUND_FOR_RESET(HttpStatus.BAD_REQUEST, "비밀번호 변경을 위한 사용자를 찾을 수 없습니다.", "USER_NOT_FOUND_FOR_RESET"),
    CACHE_NOT_FOUND(HttpStatus.NOT_FOUND, "캐시를 찾을 수 없습니다.", "CACHE_NOT_FOUND"),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "기존 비밀번호와 동일한 비밀번호입니다.", "SAME_AS_OLD_PASSWORD"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "로그인 상태에서는 사용할 수 없습니다.", "ACCESS_DENIED");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
