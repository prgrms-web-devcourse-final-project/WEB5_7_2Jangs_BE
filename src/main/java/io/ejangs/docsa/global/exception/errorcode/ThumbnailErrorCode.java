package io.ejangs.docsa.global.exception.errorcode;

import javax.swing.text.html.HTML;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ThumbnailErrorCode implements ErrorCode{

    STALE_THUMBNAIL_REQUEST(HttpStatus.CONFLICT, "싱싱하지 않은 요청입니다.", "STALE_THUMBNAIL_REQUEST");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
