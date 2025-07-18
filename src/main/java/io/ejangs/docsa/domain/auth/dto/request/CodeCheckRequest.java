package io.ejangs.docsa.domain.auth.dto.request;

import io.ejangs.docsa.domain.auth.model.CodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "인증코드 검증 요청 DTO")
public record CodeCheckRequest(

        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식을 입력해주세요.")
        String email,

        @Schema(description = "인증코드", example = "ABC123")
        @NotBlank(message = "인증코드를 입력해주세요.")
        String code,

        @Schema(description = "코드 타입 (SIGNUP 또는 RESET_PASSWORD)", example = "SIGNUP")
        @NotNull(message = "코드 타입을 입력해주세요.")
        CodeType type
) {

}
