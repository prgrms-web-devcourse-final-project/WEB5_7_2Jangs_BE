package io.ejangs.docsa.global.exception.errorcode;

import javax.swing.text.html.HTML;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ThumbnailErrorCode implements ErrorCode{

    THUMBNAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "썸네일 정보를 찾을 수 없습니다.", "THUMBNAIL_NOT_FOUND"),
    STALE_THUMBNAIL_REQUEST(HttpStatus.CONFLICT, "최신 썸네일 요청이 아닙니다.", "STALE_THUMBNAIL_REQUEST"),
    INVALID_THUMBNAIL_PURPOSE(HttpStatus.BAD_REQUEST, "썸네일 용도의 이미지가 아닙니다.", "INVALID_THUMBNAIL_PURPOSE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
