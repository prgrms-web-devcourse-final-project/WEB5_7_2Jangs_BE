package io.ejangs.docsa.domain.user.swagger;

import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "회원가입",
        description = """
                이메일 인증이 완료된 사용자가 회원 정보를 입력하여 회원가입을 완료합니다.
                `passCode`는 인증코드 검증 API(`/api/auth/code/check`)를 통해 발급된 값이어야 합니다.
                로그인 상태에서는 접근할 수 없습니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "회원가입 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = UserSignupRequest.class),
                        examples = @ExampleObject(
                                name = "회원가입 요청 예시",
                                value = """
                                        {
                                            "name": "이장님",
                                            "email": "user@example.com",
                                            "password": "Password123",
                                            "passCode": "a1b2c3d4"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "회원가입 성공",
                        content = @Content(
                                schema = @Schema(implementation = UserSignupResponse.class),
                                examples = @ExampleObject(
                                        name = "회원가입 성공 응답 예시",
                                        value = """
                                                {
                                                    "id": 1,
                                                    "name": "홍길동"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "이미 가입된 이메일",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "DUPLICATE_EMAIL",
                                        value = """
                                                {
                                                    "status": 400,
                                                    "message": "이미 가입된 이메일입니다.",
                                                    "error": "DUPLICATE_EMAIL"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "인증 코드 만료 또는 불일치",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "EXPIRED_CODE",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "인증코드가 만료되었습니다.",
                                                            "error": "EXPIRED_CODE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "INVALID_CODE",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "인증코드가 올바르지 않습니다.",
                                                            "error": "INVALID_CODE"
                                                        }
                                                        """
                                        )
                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "403",
                        description = "로그인 상태에서는 사용할 수 없음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "ACCESS_DENIED",
                                        value = """
                                                {
                                                    "status": 403,
                                                    "message": "이미 로그인된 상태입니다.",
                                                    "error": "ACCESS_DENIED"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface SignupDocs {

}
