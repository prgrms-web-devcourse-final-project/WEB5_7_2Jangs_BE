package io.ejangs.docsa.domain.doc.model;

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
        try {
            return PageDirectionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 정렬 방향입니다: " + value);
        }
    }
}

