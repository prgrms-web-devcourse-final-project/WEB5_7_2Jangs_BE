package io.ejangs.docsa.domain.branch.merge.swagger;

import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
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
        summary = "기준/비교 커밋을 바탕으로 병합 결과 브랜치와 작업장을 생성합니다.",
        description = """
                유저가 소유한 문서에서 기준 커밋과 비교 커밋을 검토한 뒤,
                병합 결과를 담은 새 브랜치와 작업장을 생성합니다.
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
                        schema = @Schema(implementation = MergeRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "branchName": "merged-branch",
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
                        description = "병합 결과를 담은 새 브랜치와 작업장 생성 성공",
                        content = @Content(
                                schema = @Schema(implementation = MergeResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "branchId": 15,
                                                    "saveId": 21
                                                }
                                                """
                                )
                        )

                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "병합용 브랜치 생성 실패 - 잘못된 요청",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "브랜치 이름이 없는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "브렌치 제목을 입력해주세요.",
                                                            "error": "VALIDATION_FAILED"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "브랜치 이름이 100자를 넘는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "브랜치이름은 100자를 초과 할 수 없습니다.",
                                                            "error": "VALIDATION_FAILED"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "동일한 커밋을 병합하는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "동일한 커밋을 병합할 수 없습니다.",
                                                            "error": "INVALID_MERGE_REQUEST"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "브랜치 이름이 중복되는 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "새로운 분기의 이름은 다른 버전의 이름과 중복될 수 없습니다.",
                                                            "error": "BRANCH_NAME_DUPLICATED"
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
                        description = "병합용 브랜치 생성 실패 - 존재하지 않는 데이터",
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
                        description = "병합용 브랜치 생성 실패 - Mongo 또는 MySQL 처리 중 오류",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "병합용 브랜치와 작업장 생성 실패",
                                                value = """
                                                        {
                                                            "status": 500,
                                                            "message": "서버 오류로 인해 병합에 실패했습니다.",
                                                            "error": "FAIL_MERGE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "Mongo 저장 중 실패",
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
public @interface MergeDocs {

}
