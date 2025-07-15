package io.ejangs.docsa.domain.doc.dto;

import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.entity.Save;

public record RecentActivityDto(
        RecentType recentType,
        Long recentTypeId
) {

    public enum RecentType {
        SAVE, COMMIT
    }

    public static RecentActivityDto from(Save save) {
        return new RecentActivityDto(RecentType.SAVE, save.getId());
    }

    public static RecentActivityDto from(Commit commit) {
        return new RecentActivityDto(RecentType.COMMIT, commit.getId());
    }
}
