package io.ejangs.docsa.domain.doc.model;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.PageErrorCode;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum PageSortType {
    UPDATED_AT("updatedAt"),
    TITLE("title");

    private final String value;

    private static final Map<String, PageSortType> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(v -> v.value.toLowerCase(), v -> v));

    PageSortType(String value) {
        this.value = value;
    }

    public static PageSortType from(String value) {
        PageSortType type = CACHE.get(value.toLowerCase());
        if (type == null) {
            throw new CustomException(PageErrorCode.UNSUPPORTED_SORT_TYPE);
        }
        return type;
    }
}
