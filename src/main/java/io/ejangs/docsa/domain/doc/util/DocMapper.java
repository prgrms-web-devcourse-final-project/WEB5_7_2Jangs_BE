package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.entity.Save;

public class DocMapper {

    public static DocCreateResponse toCreateResponse(Doc doc, Save save) {
        return new DocCreateResponse(doc.getId(), save.getId());
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

    public static DocListResponse toListResponse(Doc doc, String preview,
            RecentActivityDto recent) {
        return new DocListResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                preview,
                recent
        );
    }
}
