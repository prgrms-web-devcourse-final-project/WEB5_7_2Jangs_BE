package io.ejangs.docsa.domain.auth.swagger;

import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
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
import org.springframework.web.ErrorResponse;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "인증코드 검증",
        description = """
                이메일, 인증코드, 코드 타입(SIGNUP / RESET_PASSWORD)을 입력받아 인증코드의 유효성을 검증합니다.
                인증코드는 일정 시간 동안만 유효합니다.
                잘못된 코드, 만료된 코드, 시스템 에러 발생 시 각기 다른 응답이 반환됩니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "인증코드 검증 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = CodeCheckRequest.class),
                        examples = @ExampleObject(
                                name = "인증코드 검증 요청 예시",
                                value = """
                                        {
                                            "email": "user@example.com",
                                            "code": "ABC123",
                                            "type": "SIGNUP"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "인증코드 검증 성공",
                        content = @Content(
                                schema = @Schema(implementation = CodeCheckResponse.class),
                                examples = @ExampleObject(
                                        name = "검증 성공 응답 예시",
                                        value = """
                                                {
                                                    "passCode": "a1b2c3d4"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "잘못된 인증코드 또는 만료된 코드",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "INVALID_CODE",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "인증코드가 올바르지 않습니다.",
                                                            "error": "INVALID_CODE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "EXPIRED_CODE",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "인증코드가 만료되었습니다.",
                                                            "error": "EXPIRED_CODE"
                                                        }
                                                        """
                                        )
                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "내부 서버 오류",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "INTERNAL_ERROR",
                                        value = """
                                                {
                                                    "status": 500,
                                                    "message": "인증코드 처리 중 시스템 오류가 발생했습니다.",
                                                    "error": "INTERNAL_ERROR"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface CheckCodeDocs {

}
