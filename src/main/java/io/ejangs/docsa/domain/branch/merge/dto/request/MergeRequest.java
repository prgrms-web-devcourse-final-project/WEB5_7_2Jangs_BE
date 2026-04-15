package io.ejangs.docsa.domain.branch.merge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "병합 결과를 담은 새 브랜치 작업장 생성 요청")
public record MergeRequest(
        @Schema(description = "새로 생성할 병합 브랜치 이름", example = "merge-main-copy")
        @NotBlank(message = "브렌치 제목을 입력해주세요.")
        @Size(max = 100, message = "브랜치이름은 100자를 초과 할 수 없습니다.")
        String branchName,

        @Schema(description = "병합 기준으로 사용할 커밋 ID", example = "12")
        @NotNull(message = "기준 커밋을 선택해주세요.")
        Long baseCommitId,

        @Schema(description = "함께 병합할 대상 커밋 ID", example = "18")
        @NotNull(message = "비교할 커밋을 선택해주세요.")
        Long targetCommitId,

        @Schema(description = "프론트에서 사용자가 직접 조합한 최종 Editor.js 블록 배열")
        List<Map<String, Object>> content
) {
}
