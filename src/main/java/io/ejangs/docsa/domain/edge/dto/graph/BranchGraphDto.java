package io.ejangs.docsa.domain.edge.dto.graph;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "그래프 렌더링에 사용하는 브랜치 메타데이터")
public record BranchGraphDto(
        @Schema(description = "브랜치 ID", example = "102")
        Long id,
        @Schema(description = "브랜치 이름", example = "feature-copy")
        String name,
        @Schema(description = "브랜치 생성 시각")
        LocalDateTime createdAt,
        @Schema(description = "브랜치의 기본 시작 커밋 ID", example = "12", nullable = true)
        Long fromCommitId,
        @Schema(description = "머지 브랜치일 때 두 번째 기원 커밋 ID", example = "18", nullable = true)
        Long mergeTargetCommitId,
        @Schema(description = "브랜치의 루트 커밋 ID", example = "14", nullable = true)
        Long rootCommitId,
        @Schema(description = "브랜치의 현재 leaf 커밋 ID", example = "19", nullable = true)
        Long leafCommitId,
        @Schema(description = "브랜치에 연결된 현재 작업장 ID", example = "1001", nullable = true)
        Long saveId

) {

    public BranchGraphDto(Long id, String name, LocalDateTime createdAt, Long fromCommitId,
            Long mergeTargetCommitId, Long rootCommitId, Long leafCommitId, Long saveId) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt.plusHours(9L);
        this.fromCommitId = fromCommitId;
        this.mergeTargetCommitId = mergeTargetCommitId;
        this.rootCommitId = rootCommitId;
        this.leafCommitId = leafCommitId;
        this.saveId = saveId;
    }

}
