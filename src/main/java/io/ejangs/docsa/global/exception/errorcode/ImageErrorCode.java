package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {
    INVALID_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "업로드 가능한 최대 용량을 초과했습니다.", "INVALID_IMAGE_SIZE"),
    INVALID_IMAGE_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다.", "INVALID_IMAGE_CONTENT_TYPE"),
    IMAGE_UPLOAD_NOT_COMPLETED(HttpStatus.CONFLICT, "이미지 업로드가 아직 완료되지 않았습니다.", "IMAGE_UPLOAD_NOT_COMPLETED"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 . 없습니다.", "IMAGE_NOT_FOUND");


    private final HttpStatus status;
    private final String message;
    private final String error;

}
