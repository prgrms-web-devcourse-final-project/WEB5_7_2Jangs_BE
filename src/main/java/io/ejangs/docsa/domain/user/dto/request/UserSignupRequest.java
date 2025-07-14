package io.ejangs.docsa.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserSignupRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(min = 2, message = "이름은 최소 2자 이상이어야 합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 주소를 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "인증코드를 입력해주세요.")
        @Pattern(regexp = "^[a-z0-9]{8}$", message = "인증코드는 8자리입니다.")
        String passCode
) {

}
