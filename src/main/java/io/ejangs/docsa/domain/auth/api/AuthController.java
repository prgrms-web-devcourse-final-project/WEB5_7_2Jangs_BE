package io.ejangs.docsa.domain.auth.api;

import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/code")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입 인증코드 전송", description = "입력한 이메일 주소로 회원가입용 인증코드를 전송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증코드 전송 성공"),
            @ApiResponse(responseCode = "400", description = "이미 가입된 이메일 | DUPLICATE_EMAIL",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup-email")
    public ResponseEntity<Void> sendSignupCode(@Valid @RequestBody SignupCodeRequest request)
            throws MessagingException {
        authService.sendSignupCode(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 변경 인증코드 전송", description = "입력한 이메일 주소로 비밀번호 변경용 인증코드를 전송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증코드 전송 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음 | USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> sendResetPwdCode(@Valid @RequestBody PwdResetCodeRequest request)
            throws MessagingException {
        authService.sendResetPwdCode(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "인증코드 검증", description = "입력한 이메일, 인증코드, 코드 타입을 통해 유효성을 검증합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증코드 검증 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 인증코드 | INVALID_CODE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "인증코드 만료 | EXPIRED_CODE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "캐시 오류 등 시스템 문제 | INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/check")
    public ResponseEntity<CodeCheckResponse> checkCode(
            @Valid @RequestBody CodeCheckRequest request) {
        CodeCheckResponse response = authService.checkCode(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}
