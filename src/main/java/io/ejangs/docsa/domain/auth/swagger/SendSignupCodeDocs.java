package io.ejangs.docsa.domain.auth.swagger;

import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
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
        summary = "회원가입 인증코드 전송",
        description = """
                사용자가 입력한 이메일 주소로 회원가입을 위한 인증코드를 전송합니다.
                인증코드는 이메일로 전송되며, 제한된 시간(3분) 내에 입력되어야 합니다.
                이미 가입된 이메일 주소로 요청하는 경우 에러가 발생합니다.
                """,
        requestBody = @RequestBody(
                required = true,
                description = "회원가입 인증코드 요청 본문",
                content = @Content(
                        schema = @Schema(implementation = SignupCodeRequest.class),
                        examples = @ExampleObject(
                                name = "회원가입 인증코드 요청 예시",
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
                        responseCode = "400",
                        description = "이미 가입된 이메일 주소",
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
                )
        }
)
public @interface SendSignupCodeDocs {

}

