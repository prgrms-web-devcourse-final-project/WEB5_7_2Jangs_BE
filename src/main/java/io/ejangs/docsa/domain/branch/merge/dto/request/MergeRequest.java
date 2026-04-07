package io.ejangs.docsa.domain.branch.merge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record MergeRequest(
        @NotBlank(message = "브렌치 제목을 입력해주세요.")
        @Size(max = 100, message = "브랜치이름은 100자를 초과 할 수 없습니다.")
        String branchName,

        @NotNull(message = "기준 커밋을 선택해주세요.")
        Long baseCommitId,

        @NotNull(message = "비교할 커밋을 선택해주세요.")
        Long targetCommitId,

        List<Map<String, Object>> content
) {
}
