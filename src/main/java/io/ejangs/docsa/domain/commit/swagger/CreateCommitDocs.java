package io.ejangs.docsa.domain.commit.swagger;

import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
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
        summary = "유저가 새로운 기록(commit)을 만듭니다.",
        description = """
                유저가 소유한 문서에서 새로운 기록(commit)을 만듭니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "기록하려는 문서의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "Idempotency-Key",
                        description = "생성 요청을 식별하는 UUID. 같은 요청을 재시도할 때 같은 값을 사용합니다.",
                        example = "550e8400-e29b-41d4-a716-446655440000",
                        required = true,
                        in = ParameterIn.HEADER,
                        schema = @Schema(type = "string", format = "uuid")
                )
        },
        requestBody = @RequestBody(
                content = @Content(
                        schema = @Schema(implementation = CreateCommitRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "title": "문서 초안 작성 완료",
                                            "description": "1차 초안 커밋입니다.",
                                            "branchId": 1,
                                            "blocks": [
                                                {
                                                    "id": "mhTl6ghSkV",
                                                    "type": "paragraph",
                                                    "data": {
                                                        "text": "Hey. Meet the new Editor. On this picture you can see it in action. Then, try a demo 🤓"
                                                    }
                                                },
                                                {
                                                    "id": "os_YI4eub4",
                                                    "type": "list",
                                                    "data": {
                                                        "type": "unordered",
                                                        "items": [
                                                            "It is a block-style editor",
                                                            "It returns clean data output in JSON",
                                                            "Designed to be extendable and pluggable with a <a href=\\"https://editorjs.io/creating-a-block-tool\\">simple API</a>"
                                                        ]
                                                    }
                                                }
                                            ],
                                            "blockOrders": [
                                                "mhTl6ghSkV",
                                                "l98dyx3yjb",
                                                "os_YI4eub4"
                                            ]
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "기록(commit) 성공 후 생성된 기록 id 반환",
                        content = @Content(
                                schema = @Schema(implementation = CreateCommitResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "id": 24
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "기록 (commit) 실패 - 잘못된 요청",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "기록 제목이 없는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "기록 제목을 입력해주세요.",
                                                            "error": "UNEXPECTED_ERROR"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "기록 제목이 30자를 넘는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "기록 제목은 30자를 초과 할 수 없습니다.",
                                                            "error": "UNEXPECTED_ERROR"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "설명이 100자가 넘는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "기록에 대한 설명은 100자를 초과 할 수 없습니다.",
                                                            "error": "UNEXPECTED_ERROR"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "문단의 순서가 유효하지 않은 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "문단의 순서가 유효하지 않습니다.",
                                                            "error": "BLOCK_SEQUENCE_INVALID"
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
                        responseCode = "404",
                        description = "기록 (commit) 실패 - 존재하지 않는 데이터",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "해당 문서에 속한 브랜치가 아닐 경우",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "해당 브랜치를 찾을 수 없습니다.",
                                                            "error": "BRANCH_NOT_FOUND_OR_FORBIDDEN"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "문단의 순서를 찾을 수 없습니다.",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "문단의 순서를 찾을 수 없습니다.",
                                                            "error": "BLOCK_SEQUENCE_NOT_FOUND"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "존재하지 않는 브랜치",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "해당 브랜치를 찾을 수 없습니다.",
                                                            "error": "BRANCH_NOT_FOUND"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "존재하지 않는 문서",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "해당 문서를 찾을 수 없습니다.",
                                                            "error": "DOCUMENT_NOT_FOUND"
                                                        }
                                                        """
                                        )
                                }
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
                        description = "기록 생성 실패 - MySQL 또는 MongoDB 데이터 처리 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "MySQL 또는 MongoDB 데이터 처리 실패",
                                                value = """
                                                        {
                                                            "status": 500,
                                                            "message": "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                                                            "error": "DATABASE_ERROR"
                                                        }
                                                        """
                                        )
                                }
                        )
                )

        }
)
public @interface CreateCommitDocs {

}
