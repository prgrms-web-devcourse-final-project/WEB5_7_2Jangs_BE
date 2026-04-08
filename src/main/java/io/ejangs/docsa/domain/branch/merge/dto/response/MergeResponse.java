package io.ejangs.docsa.domain.branch.merge.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "병합 결과로 생성된 새 브랜치와 작업장 정보")
public record MergeResponse(
        @Schema(description = "새로 생성된 병합 브랜치 ID", example = "15")
        Long branchId,
        @Schema(description = "새 브랜치에 연결된 작업장 ID", example = "21")
        Long saveId
) {
}
