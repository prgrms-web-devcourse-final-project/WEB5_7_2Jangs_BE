package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.app.CommitContentAssembler;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dto.RecentActivityDto;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.save.dao.mongodb.SaveContentRepository;
import io.ejangs.docsa.domain.save.document.SaveContent;
import io.ejangs.docsa.domain.save.entity.Save;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.SaveErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocListAssembler {

    private final CommitContentAssembler commitContentAssembler;
    private final SaveContentRepository saveContentRepository;

    private static final String DEFAULT_PREVIEW = "미리보기 없음";

    public Page<DocPageResponse> assembleDocList(Page<Doc> docs) {
        return docs
                .map(doc -> {
                    Branch recentBranch = getMostRecentBranch(doc);
                    RecentActivityDto recent = getRecentActivity(recentBranch);
                    String preview = extractPreviewSafe(recentBranch, recent);
                    return DocMapper.toListResponse(doc, preview, recent);
                });
    }

    public Page<DocSimplePageResponse> assembleDocListSimple(Page<Doc> docs) {
        return docs
                .map(doc -> {
                    Branch recentBranch = getMostRecentBranch(doc);
                    RecentActivityDto recent = getRecentActivity(recentBranch);
                    return DocMapper.toListSimpleResponse(doc, recent);
                });
    }

    private String extractPreviewSafe(Branch branch, RecentActivityDto recent) {
        if (branch == null || recent == null) {
            return DEFAULT_PREVIEW;
        }

        return switch (recent.recentType()) {
            case COMMIT -> extractPreviewFromCommit(branch.getLeafCommit());
            case SAVE -> extractPreviewFromSave(branch.getSave());
            default -> DEFAULT_PREVIEW;
        };
    }

    private String extractPreviewFromCommit(Commit commit) {
        if (commit == null) {
            return DEFAULT_PREVIEW;
        }

        List<Map<String, Object>> content = commitContentAssembler.assemble(
                commit.getCommitMongoId());
        return PreviewExtractor.doExtractPreview(content);
    }

    private String extractPreviewFromSave(Save save) {
        if (save == null) {
            return DEFAULT_PREVIEW;
        }

        SaveContent saveContent = saveContentRepository.findById(save.getSaveMongoId())
                .orElseThrow(() -> new CustomException(SaveErrorCode.SAVE_NOT_FOUND));

        List<Map<String, Object>> content = saveContent.getContent();
        return PreviewExtractor.doExtractPreview(content);
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
