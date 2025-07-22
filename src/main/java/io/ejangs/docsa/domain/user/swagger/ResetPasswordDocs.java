package io.ejangs.docsa.domain.user.swagger;

import io.ejangs.docsa.domain.user.dto.request.PasswordResetRequest;
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
        summary = "비밀번호 변경",
        description = """
                인증이 완료된 사용자가 비밀번호를 변경합니다.
                `passCode`는 인증코드 검증 API(`/api/auth/code/check`)를 통해 발급된 값이어야 합니다.
                비밀번호는 대소문자+숫자를 포함하여 8자 이상이어야 합니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "비밀번호 변경 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = PasswordResetRequest.class),
                        examples = @ExampleObject(
                                name = "비밀번호 변경 요청 예시",
                                value = """
                                        {
                                            "email": "user@example.com",
                                            "password": "NewPassword123",
                                            "passCode": "a1b2c3d4"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "비밀번호 변경 성공"
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "기존 비밀번호와 동일함",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "SAME_AS_OLD_PASSWORD",
                                        value = """
                                                {
                                                    "status": 400,
                                                    "message": "기존 비밀번호와 동일합니다.",
                                                    "error": "SAME_AS_OLD_PASSWORD"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "사용자 없음 또는 인증 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "USER_NOT_FOUND",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "해당 이메일의 사용자를 찾을 수 없습니다.",
                                                            "error": "USER_NOT_FOUND"
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
                        responseCode = "500",
                        description = "시스템 내부 오류",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "INTERNAL_ERROR",
                                        value = """
                                                {
                                                    "status": 500,
                                                    "message": "비밀번호 변경 처리 중 시스템 오류가 발생했습니다.",
                                                    "error": "INTERNAL_ERROR"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface ResetPasswordDocs {

}
