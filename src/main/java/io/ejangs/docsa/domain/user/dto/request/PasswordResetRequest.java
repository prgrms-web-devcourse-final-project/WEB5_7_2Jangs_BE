package io.ejangs.docsa.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 변경 요청 DTO")
public record PasswordResetRequest(

        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 주소를 입력해주세요.")
        String email,

        @Schema(description = "새 비밀번호 (대소문자+숫자 조합 8자 이상)", example = "NewPassword123")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "비밀번호는 대소문자와 숫자를 포함해야 합니다."
        )
        String password,

        @Schema(description = "검증 완료를 나타내는 코드", example = "a1b2c3d4")
        @NotBlank(message = "인증코드를 입력해주세요.")
        @Pattern(regexp = "^[a-z0-9]{8}$", message = "인증코드는 8자리입니다.")
        String passCode
) {

}
