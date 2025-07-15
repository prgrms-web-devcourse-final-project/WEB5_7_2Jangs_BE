package io.ejangs.docsa.global.util;

import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.save.entity.Save;

public class RenewUpdatedAtHelper {

    // 브랜치 변경감지시 브랜치-문서 갱신
    public static void touch(Branch branch) {
        branch.updateTimestamp();
        if (branch.getDoc() != null) {
            branch.getDoc().updateTimestamp();
        }
    }

    // 저장 변경감지시 저장-브랜치-문서 갱신
    public static void touch(Save save) {
        save.updateTimestamp();
        if (save.getBranch() != null) {
            touch(save.getBranch());
        }
    }

    // 기록 변경감지시 기록-브랜치-문서 갱신
    public static void touch(Commit commit) {
        commit.updateTimestamp();
        if (commit.getBranch() != null) {
            touch(commit.getBranch());
        }
    }
}
