package io.ejangs.docsa.domain.auth.api;

import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/code")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup-email")
    public ResponseEntity<Void> sendSignupCode(@Valid @RequestBody SignupCodeRequest request) throws MessagingException {
        authService.sendSignupCode(request);
        return ResponseEntity.ok().build();
    }
}
