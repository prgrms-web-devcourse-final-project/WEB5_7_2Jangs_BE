package io.ejangs.docsa.domain.auth.swagger;

import io.ejangs.docsa.domain.auth.dto.response.SessionCheckResponse;
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
import org.springframework.http.MediaType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "세션 유효성 확인",
        description = "현재 로그인된 사용자의 세션이 유효한지 확인하고 사용자 정보를 반환합니다.",
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "세션 유효성 확인 성공",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = SessionCheckResponse.class),
                                examples = @ExampleObject(
                                        name = "성공 응답 예시",
                                        value = """
                                                {
                                                    "id": 1,
                                                    "name": "이장님"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증되지 않은 사용자",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "AUTHENTICATION_FAILED",
                                        value = """
                                                {
                                                    "status": 401,
                                                    "message": "인증에 실패했습니다.",
                                                    "error": "AUTHENTICATION_FAILED"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "404",
                        description = "존재하지 않는 사용자",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        name = "USER_NOT_FOUND",
                                        value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 사용자를 찾을 수 없습니다.",
                                                    "error": "USER_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface CheckSessionDocs {

}