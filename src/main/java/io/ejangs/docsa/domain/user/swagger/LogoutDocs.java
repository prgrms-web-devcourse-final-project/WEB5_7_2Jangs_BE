package io.ejangs.docsa.domain.user.swagger;

import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "로그아웃",
        description = """
                현재 로그인된 세션을 무효화하여 로그아웃합니다.
                클라이언트는 세션 쿠키(`JSESSIONID`)를 포함한 상태에서 요청해야 합니다.
                세션이 없는 상태에서 요청하면 인증 오류가 발생합니다.
                """,
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "로그아웃 성공"
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "로그인되지 않은 상태",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "LOGIN_REQUIRED",
                                        value = """
                                                {
                                                    "status": 401,
                                                    "message": "로그인이 필요합니다.",
                                                    "error": "LOGIN_REQUIRED"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface LogoutDocs {

}

