package io.ejangs.docsa.domain.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
    @NotBlank(message = "문서제목을 입력해주세요.")
    @Size(max = 50, message = "문서제목은 50자를 초과 할 수 없습니다.")
    String title
) {

}
