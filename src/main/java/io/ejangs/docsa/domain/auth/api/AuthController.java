package io.ejangs.docsa.domain.auth.api;

import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.ejangs.docsa.domain.auth.dto.response.SessionCheckResponse;
import io.ejangs.docsa.domain.auth.swagger.CheckCodeDocs;
import io.ejangs.docsa.domain.auth.swagger.SendResetPwdCodeDocs;
import io.ejangs.docsa.domain.auth.swagger.SendSignupCodeDocs;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Auth API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/code/signup-email")
    @SendSignupCodeDocs
    public ResponseEntity<Void> sendSignupCode(@Valid @RequestBody SignupCodeRequest request)
            throws MessagingException {
        authService.sendSignupCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/code/reset-password")
    @SendResetPwdCodeDocs
    public ResponseEntity<Void> sendResetPwdCode(@Valid @RequestBody PwdResetCodeRequest request)
            throws MessagingException {
        authService.sendResetPwdCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/code/check")
    @CheckCodeDocs
    public ResponseEntity<CodeCheckResponse> checkCode(
            @Valid @RequestBody CodeCheckRequest request) {
        CodeCheckResponse response = authService.checkCode(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/session/check")
    public ResponseEntity<SessionCheckResponse> checkSession(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        SessionCheckResponse response = authService.checkSession(userDetails.getId());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}
