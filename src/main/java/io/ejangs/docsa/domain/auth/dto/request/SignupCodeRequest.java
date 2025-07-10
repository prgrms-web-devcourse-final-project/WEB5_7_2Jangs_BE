package io.ejangs.docsa.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupCodeRequest(
        @NotBlank @Email String email
) {

}
