package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.dto.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.entity.Doc;

public class DocMapper {

    public static DocCreateResponse toCreateResponse(Doc doc) {
        return new DocCreateResponse(doc.getId());
    }

    public static DocTitleUpdateResponse toUpdateResponse(Doc doc) {
        return new DocTitleUpdateResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getUpdatedAt()
        );
    }

    public static DocListSimpleResponse toListSimpleResponse(Doc doc, RecentActivityDto recent) {
        return new DocListSimpleResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                recent
        );
    }
}
