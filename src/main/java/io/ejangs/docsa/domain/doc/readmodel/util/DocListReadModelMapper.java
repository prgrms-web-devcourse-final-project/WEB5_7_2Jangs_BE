package io.ejangs.docsa.domain.doc.readmodel.util;

import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.readmodel.document.DocListReadModel;

public final class DocListReadModelMapper {

    private DocListReadModelMapper() {
    }

    public static DocSimplePageResponse toListSimpleResponse(DocListReadModel model) {
        return new DocSimplePageResponse(
                model.getId(),
                model.getTitle(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                model.getRecentSaveId()
        );
    }

    public static DocPageResponse toListResponse(DocListReadModel model, String cdnUrl) {
        return new DocPageResponse(
                model.getId(),
                model.getTitle(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                buildThumbnailUrl(model.getThumbnailObjectKey(), cdnUrl),
                model.getThumbnailStatus(),
                model.getRecentSaveId()
        );
    }

    private static String buildThumbnailUrl(String objectKey, String cdnUrl) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return "%s/%s".formatted(cdnUrl, objectKey);
    }
}
