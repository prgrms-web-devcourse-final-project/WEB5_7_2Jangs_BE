package io.ejangs.docsa.domain.user.api;

import io.ejangs.docsa.domain.user.app.UserService;
import io.ejangs.docsa.domain.user.dto.request.UserLoginRequest;
import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserLoginResponse;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<UserSignupResponse> signup(
            @Valid @RequestBody UserSignupRequest request) {
        UserSignupResponse response = userService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(
            @Valid @RequestBody UserLoginRequest request,
            HttpServletRequest httpRequest) {
        UserLoginResponse response = userService.login(request, httpRequest);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        userService.logout(httpRequest, httpResponse);
        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }
}
