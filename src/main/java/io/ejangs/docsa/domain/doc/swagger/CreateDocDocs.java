package io.ejangs.docsa.domain.doc.swagger;

import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "문서 생성",
        description = "문서를 생성하면 기본 브랜치와 저장을 생성하고, 생성된 문서와 저장의 ID를 반환",
        parameters = @Parameter(
                name = "Idempotency-Key",
                description = "생성 요청을 식별하는 UUID. 같은 요청을 재시도할 때 같은 값을 사용합니다.",
                example = "550e8400-e29b-41d4-a716-446655440000",
                required = true,
                in = ParameterIn.HEADER,
                schema = @Schema(type = "string", format = "uuid")
        ),
        requestBody = @RequestBody(
                content = @Content(
                        schema = @Schema(implementation = DocTitleRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "title": "new Title"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "문서 생성 성공(기본 브랜치/저장 생성)",
                        content = @Content(
                                schema = @Schema(implementation = DocCreateResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "id": 1,
                                                    "saveId": 1
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "유효하지 않은 문서 제목 요청",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "이미 존재하는 문서 제목",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "이미 사용중인 제목입니다.",
                                                            "error": "TITLE_DUPLICATION"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "문서 제목이 빈칸(null, 공백 등)",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "문서제목을 입력해주세요.",
                                                            "error": "VALIDATION_FAILED"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "문서 제목이 최대 길이 초과(50자)",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "문서제목은 50자를 초과 할 수 없습니다.",
                                                            "error": "VALIDATION_FAILED"
                                                        }
                                                        """
                                        )
                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 로그인 세션이 없음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 401,
                                                    "message": "로그인이 필요합니다.",
                                                    "error": "LOGIN_REQUIRED"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "409",
                        description = "생성 작업 충돌 - CREATE_OPERATION_IN_PROGRESS, "
                                + "CREATE_OPERATION_CANCELLED 또는 IDEMPOTENCY_KEY_REUSED",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "서버오류로 인한 문서 생성 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 500,
                                                    "message": "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                                                    "error": "DATABASE_ERROR"
                                                }
                                                """
                                )
                        )
                ),
        }
)
public @interface CreateDocDocs {

}
