package io.ejangs.docsa.domain.doc.model;

import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.PageErrorCode;
import lombok.Getter;
import org.springframework.data.domain.Sort;

@Getter
public enum PageDirectionType {
    ASC(Sort.Direction.ASC),
    DESC(Sort.Direction.DESC);

    private final Sort.Direction direction;


    PageDirectionType(Sort.Direction direction) {
        this.direction = direction;
    }

    public static PageDirectionType from(String value) {
        return switch (value.toUpperCase()) {
            case "ASC" -> PageDirectionType.ASC;
            case "DESC" -> PageDirectionType.DESC;
            default -> throw new CustomException(PageErrorCode.UNSUPPORTED_DIRECTION_TYPE);
        };
    }

}

