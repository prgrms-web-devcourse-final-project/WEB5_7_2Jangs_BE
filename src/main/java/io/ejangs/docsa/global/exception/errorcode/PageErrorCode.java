package io.ejangs.docsa.global.exception.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PageErrorCode implements ErrorCode {
    UNSUPPORTED_SORT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 기준입니다.", "UNSUPPORTED_SORT_TYPE"),
    UNSUPPORTED_DIRECTION_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 방향입니다.",
            "UNSUPPORTED_DIRECTION_TYPE"),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE"),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 0 이상이어야 합니다.", "INVALID_PAGE_SIZE");

    private final HttpStatus status;
    private final String message;
    private final String error;
}
