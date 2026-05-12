package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.dao.ThumbnailRepository;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocListAssembler {

    private final ThumbnailRepository thumbnailRepository;

    @Value("${cloud.aws.s3.public-base-url}")
    private String cdnUrl;

    public Page<DocPageResponse> assembleDocList(Page<Doc> docs) {
        List<Long> docIds = docs.getContent().stream()
                .map(Doc::getId)
                .toList();

        Map<Long, Thumbnail> thumbnailByDocId = thumbnailRepository
                .findAllByDocIdInWithCurrentImage(docIds)
                .stream()
                .collect(Collectors.toMap(
                        thumbnail -> thumbnail.getDoc().getId(),
                        Function.identity()
                ));

        return docs.map(doc -> {
            Branch recentBranch = getMostRecentBranch(doc);
            RecentActivityDto recent = getRecentActivity(recentBranch);
            Thumbnail thumbnail = thumbnailByDocId.get(doc.getId());

            return DocMapper.toListResponse(
                    doc,
                    buildThumbnailUrl(thumbnail),
                    thumbnailStatusOf(thumbnail),
                    recent
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
        return docs
                .map(doc -> {
                    Branch recentBranch = getMostRecentBranch(doc);
                    RecentActivityDto recent = getRecentActivity(recentBranch);
                    return DocMapper.toListSimpleResponse(doc, recent);
                });
    }

    private Branch getMostRecentBranch(Doc doc) {
        return doc.getBranches().stream()
                .max(Comparator.comparing(Branch::getUpdatedAt))
                .orElse(null);
    }

    private RecentActivityDto getRecentActivity(Branch branch) {
        if (branch.getSave() != null) {
            return RecentActivityDto.from(branch.getSave());
        }
        if (branch.getLeafCommit() != null) {
            return RecentActivityDto.from(branch.getLeafCommit());
        }
        return null;
    }


}
