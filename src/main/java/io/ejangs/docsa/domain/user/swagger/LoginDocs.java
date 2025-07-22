package io.ejangs.docsa.domain.user.swagger;

import io.ejangs.docsa.domain.user.dto.request.UserLoginRequest;
import io.ejangs.docsa.domain.user.dto.response.UserLoginResponse;
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
        summary = "로그인",
        description = """
                사용자가 이메일과 비밀번호를 입력하여 로그인을 시도합니다.
                세션 방식으로 로그인하며, 성공 시 JSESSIONID 쿠키가 발급됩니다.
                이미 로그인된 상태에서는 로그인할 수 없습니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "로그인 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = UserLoginRequest.class),
                        examples = @ExampleObject(
                                name = "로그인 요청 예시",
                                value = """
                                        {
                                            "email": "user@example.com",
                                            "password": "Password123"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "로그인 성공",
                        content = @Content(
                                schema = @Schema(implementation = UserLoginResponse.class),
                                examples = @ExampleObject(
                                        name = "로그인 성공 응답 예시",
                                        value = """
                                                {
                                                    "id": 1
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "이메일 또는 비밀번호가 일치하지 않음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "INVALID_CREDENTIALS",
                                        value = """
                                                {
                                                    "status": 401,
                                                    "message": "이메일 또는 비밀번호가 잘못되었습니다.",
                                                    "error": "INVALID_CREDENTIALS"
                                                }
                                                """
                                )
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
public @interface LoginDocs {

}
