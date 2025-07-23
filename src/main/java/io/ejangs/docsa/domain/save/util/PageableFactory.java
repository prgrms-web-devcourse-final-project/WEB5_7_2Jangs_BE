package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.doc.model.PageDirectionType;
import io.ejangs.docsa.domain.doc.model.PageSortType;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.PageErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageableFactory {

    public static Pageable create(String sortParam, String dirParam, int page, int size) {
        if (page < 0) {
            throw new CustomException(PageErrorCode.INVALID_PAGE);
        }
        if (size <= 0) {
            throw new CustomException(PageErrorCode.INVALID_PAGE_SIZE);
        }

        PageSortType sort = PageSortType.from(sortParam);
        PageDirectionType dir = PageDirectionType.from(dirParam);

        return PageRequest.of(page, size, Sort.by(dir.getDirection(), sort.getValue()));
    }
}
