package io.ejangs.docsa.domain.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
        @NotBlank(message = "브랜치 이름은 필수입니다")
        @Size(max = 100, message = "브랜치이름은 100자를 초과 할 수 없습니다.")
        String name,

        @NotNull(message = "이어서 작업할 브랜치를 선택해주세요.")
        Long fromCommitId
) {}
