package io.ejangs.docsa.global.exception;

import io.ejangs.docsa.global.exception.errorcode.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

public record ErrorResponse(Integer status, String message, String error) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getMessage(),
                errorCode.getError());
    }

    public static ErrorResponse from(FieldError fieldError) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), fieldError.getDefaultMessage(),
                "VALIDATION_FAILED");
    }

    public static ErrorResponse from(HttpStatus status, String message, String error) {
        return new ErrorResponse(status.value(), message, error);
    }

    public static ErrorResponse from(String message) {
        return from(HttpStatus.BAD_REQUEST, message, "UNEXPECTED_ERROR");
    }
}
