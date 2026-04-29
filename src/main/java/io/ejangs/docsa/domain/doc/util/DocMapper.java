package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail.ThumbnailStatus;
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

    public static DocSimplePageResponse toListSimpleResponse(Doc doc, RecentActivityDto recent) {
        return new DocSimplePageResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                recent
        );
    }

    public static DocPageResponse toListResponse(Doc doc, String thumbnailUrl,
            ThumbnailStatus thumbnailStatus,
            RecentActivityDto recent) {
        return new DocPageResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                thumbnailUrl,
                thumbnailStatus,
                recent
        );
    }
}
