package io.ejangs.docsa.domain.auth.swagger;

import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
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
        summary = "비밀번호 변경 인증코드 전송",
        description = """
                사용자가 입력한 이메일 주소로 비밀번호 재설정을 위한 인증코드를 전송합니다.
                해당 이메일이 등록된 사용자 계정이어야 하며, 인증코드는 일정 시간 동안 유효합니다.
                존재하지 않는 사용자에게 요청 시 에러가 발생합니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "비밀번호 변경 인증코드 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = PwdResetCodeRequest.class),
                        examples = @ExampleObject(
                                name = "비밀번호 변경 인증코드 요청 예시",
                                value = """
                                        {
                                            "email": "user@example.com"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "인증코드 전송 성공"
                ),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 사용자",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "USER_NOT_FOUND",
                                        value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 이메일의 사용자를 찾을 수 없습니다.",
                                                    "error": "USER_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface SendResetPwdCodeDocs {

}

