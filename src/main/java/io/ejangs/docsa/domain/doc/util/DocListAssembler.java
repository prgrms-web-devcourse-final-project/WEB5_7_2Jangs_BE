package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.branch.dao.mysql.BranchRepository;
import io.ejangs.docsa.domain.doc.dto.LatestSaveIdDto;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.dao.ThumbnailRepository;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocListAssembler {

    private final BranchRepository branchRepository;

    @Value("${cloud.aws.s3.public-base-url}")
    private String cdnUrl;

    public Page<DocPageResponse> assembleDocList(Page<Doc> docs) {
        List<Long> docIds = docs.getContent().stream()
                .map(Doc::getId)
                .toList();

        Map<Long, Long> latestSaveIdByDocId = latestSaveIdByDocId(docIds);

        return docs.map(doc -> {
            Thumbnail thumbnail = doc.getThumbnail();

            return DocMapper.toListResponse(
                    doc,
                    buildThumbnailUrl(thumbnail),
                    thumbnailStatusOf(thumbnail),
                    latestSaveIdByDocId.get(doc.getId())
            );
        });
    }

    private String buildThumbnailUrl(Thumbnail thumbnail) {
        if (thumbnail == null || thumbnail.getCurrentImage() == null) {
            return null;
        }

        return "%s/%s".formatted(cdnUrl, thumbnail.getCurrentImage().getObjectKey());
    }

    private Thumbnail.ThumbnailStatus thumbnailStatusOf(Thumbnail thumbnail) {
        if (thumbnail == null) {
            return Thumbnail.ThumbnailStatus.EMPTY;
        }
        return thumbnail.getStatus();
    }


    public Page<DocSimplePageResponse> assembleDocListSimple(Page<Doc> docs) {
        List<Long> docIds = docs.getContent().stream()
                .map(Doc::getId)
                .toList();
        Map<Long, Long> latestSaveIdByDocId = latestSaveIdByDocId(docIds);

        return docs.map(doc -> DocMapper.toListSimpleResponse(
                doc,
                latestSaveIdByDocId.get(doc.getId())
        ));
    }

    private Map<Long, Long> latestSaveIdByDocId(List<Long> docIds) {
        if (docIds.isEmpty()) {
            return Map.of();
        }

        return branchRepository.findLatestSaveIdsByDocIds(docIds)
                .stream()
                .collect(Collectors.toMap(
                        LatestSaveIdDto::docId,
                        LatestSaveIdDto::saveId
                ));
    }
}
