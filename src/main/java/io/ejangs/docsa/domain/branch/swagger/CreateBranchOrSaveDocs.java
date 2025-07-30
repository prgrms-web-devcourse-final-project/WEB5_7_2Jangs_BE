package io.ejangs.docsa.domain.branch.swagger;

import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
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
        summary = "이어서 작업하기",
        description = """
                새로운 저장을 만듭니다. 직전에 선택한 직전 기록의 종류에 따라 다음을 실행합니다:
                
                - 최신기록에서 이어서 작업할 경우 그 버전에 새로운 저장 생성
                - 아니라면 새로운 버전 생성 후 새로운 저장 생성
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "저장이 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        requestBody = @RequestBody(
                content = @Content(
                        schema = @Schema(implementation = BranchCreateRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "name": "main",
                                            "fromCommitId": 10
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "이어서 작업하기 성공 (새로운 저장 or 새로운 버전&저장 생성)",
                        content = @Content(
                                schema = @Schema(implementation = BranchCreateResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                	"branchId" : 1,
                                                	"saveId": 4
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "이어서 작업하기 실패 - 잘못된 요청",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "요청으로 온 fromCommitId 이 null",
                                                value = """
                                                {
                                                    "status": 400,
                                                    "message": "잘못된 요청입니다.",
                                                    "error": "INVALID_FROM_COMMIT"
                                                }
                                                """
                                        ),
                                        @ExampleObject(
                                                name = "기록이 해당 문서에 포함되어 있지 않음",
                                                value = """
                                                {
                                                    "status": 400,
                                                    "message": "기록이 해당 문서에 속해있지 않습니다.",
                                                    "error": "COMMIT_NOT_IN_DOCUMENT"
                                                }
                                                """
                                        ),
                                        @ExampleObject(
                                                name = "생성하려는 버전의 이름이 이미 다른 버전의 이름으로 존재함",
                                                value = """
                                                {
                                                    "status": 400,
                                                    "message": "새로운 버전의 이름은 다른 버전의 이름과 중복될 수 없습니다.",
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
                        description = "이어서 작업하기 실패 - 존재하지 않는 id",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "존재하지 않는 문서",
                                                value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 문서를 찾을 수 없습니다.",
                                                    "error": "DOCUMENT_NOT_FOUND"
                                                }
                                                """
                                        ),
                                        @ExampleObject(
                                                name = "존재하지 않는 기록",
                                                value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 기록을 찾을 수 없습니다.",
                                                    "error": "COMMIT_NOT_FOUND"
                                                }
                                                """
                                        )
                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "이어서 작업하기 실패 - MySQL 또는 MongoDB 저장 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "MySQL 또는 MongoDB에 저장 실패",
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
public @interface CreateBranchOrSaveDocs {

}
