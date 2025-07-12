package io.ejangs.docsa.domain.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchRenameRequest (
    @NotBlank(message = "브랜치 이름은 빈 값일 수 없습니다")
    @Size(max = 100, message = "브랜치이름은 100자를 초과 할 수 없습니다.")
    String newName

){}
