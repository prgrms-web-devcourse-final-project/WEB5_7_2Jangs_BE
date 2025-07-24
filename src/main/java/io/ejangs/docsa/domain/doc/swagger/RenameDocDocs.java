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
        summary = "문서 제목 수정",
        description = "문서의 제목을 수정합니다.",
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "제목을 수정할 문서의 ID",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
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
                        responseCode = "200",
                        description = "문서 제목 수정 성공",
                        content = @Content(
                                schema = @Schema(implementation = DocCreateResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "id": 1,
                                                    "title": "Updated Title",
                                                    "updatedAt": "2025-07-07T10:15:30Z"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "유효하지 않은 문서 제목 요청 및 존재하지 않는 문서",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "존재하지 않는 문서 ID",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "해당 문서를 찾을 수 없습니다.",
                                                            "error": "DOCUMENT_NOT_FOUND"
                                                        }
                                                        """
                                        ),
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
                                        ),
                                        @ExampleObject(
                                                name = "현재 문서의 제목과 같은 제목으로 수정요청",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "현재 제목과 동일한 제목입니다.",
                                                            "error": "SAME_AS_CURRENT_TITLE"
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
        }
)
public @interface RenameDocDocs {

}