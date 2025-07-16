package io.ejangs.docsa.domain.auth.dto.request;

import io.ejangs.docsa.domain.auth.model.CodeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CodeCheckRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식을 입력해주세요.")
        String email,

        @NotBlank(message = "인증코드를 입력해주세요.")
        String code,

        @NotNull(message = "코드 타입을 입력해주세요.")
        CodeType type
) {

}
