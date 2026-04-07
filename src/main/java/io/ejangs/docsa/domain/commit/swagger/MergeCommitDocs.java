package io.ejangs.docsa.domain.commit.swagger;

import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
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
        summary = "request 에 담긴 2개의 commit id 를 기반으로 병합 합니다.",
        description = """
                유저가 소유한 문서에서 기록 2개를 병합 합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "병합 하려는 문서의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        requestBody = @RequestBody(
                content = @Content(
                        schema = @Schema(implementation = MergeCommitRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "branchName": "merged-branch",
                                            "title": "문서 병합 완료",
                                            "description": "병합 커밋입니다.",
                                            "baseCommitId": 1,
                                            "targetCommitId": 2,
                                            "content": [
                                                {
                                                    "id": "mhTl6ghSkV",
                                                    "type": "paragraph",
                                                    "data": {
                                                        "text": "Hey. Meet the new Editor. On this picture you can see it in action. Then, try a demo 🤓"
                                                    }
                                                },
                                                {
                                                    "id": "l98dyx3yjb",
                                                    "type": "header",
                                                    "data": {
                                                        "text": "Key features",
                                                        "level": 3
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
                                            ]
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "기록 병합 성공 후 새로운 기록 생성 및 id 할당",
                        content = @Content(
                                schema = @Schema(implementation = CreateCommitResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "id": 15
                                                }
                                                """
                                )
                        )

                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "기록(commit) 병합 실패 - 잘못된 요청",
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
                                                name = "Leaf Commit이 필요한 상황에 전달 받은 기록이 Leaf Commit이 아닌 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "버전의 마지막 기록이 아닙니다.",
                                                            "error": "IS_NOT_LEAF_COMMIT"
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
                        description = "기록(commit) merge 실패 - 존재하지 않는 데이터",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "존재하지 않는 기록",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "해당 기록을 찾을 수 없습니다.",
                                                            "error": "COMMIT_NOT_FOUND"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "해당 문서에 속한 버전이 아닐 경우",
                                                value = """
                                                        {
                                                            "status": 404,
                                                            "message": "해당 버전을 찾을 수 없습니다.",
                                                            "error": "BRANCH_NOT_FOUND_OR_FORBIDDEN"
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
public @interface MergeCommitDocs {

}
