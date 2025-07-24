package io.ejangs.docsa.global.exception;

import io.ejangs.docsa.global.exception.errorcode.DatabaseErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("CustomException 발생 : {}", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ErrorResponse.from(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();

        if (fieldError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.from(fieldError));
        }

        String errorMessage = e.getMessage();
        log.error("MethodArgumentNotValidException 발생 : {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.from(errorMessage));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        log.error("DataAccessException 발생 : {}", e.getMessage());
        return ResponseEntity.status(DatabaseErrorCode.MYSQL_ERROR.getStatus())
                .body(ErrorResponse.from(DatabaseErrorCode.MYSQL_ERROR));
    }
}
