package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다.", "DUPLICATE_EMAIL"),
    INVALID_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다.", "INVALID_CODE"),
    EXPIRED_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다.", "EXPIRED_CODE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
