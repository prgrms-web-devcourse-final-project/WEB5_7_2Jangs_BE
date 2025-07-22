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
        summary = "이어서 작업하기 API",
        description = "최신 커밋이라면 새로운 저장 생성, 아니라면 새로운 브랜치 + 저장 생성",
        parameters = {
                @Parameter(
                        name = "documentId",
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
                        description = "이어서 작업하기 성공 (새로운 저장 or 저장/브랜치 생성",
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
                                                    "message": "요청이 잘못되었습니다.",
                                                    "error": "INVALID_FROM_COMMIT"
                                                }
                                                """
                                        ),
                                        @ExampleObject(
                                                name = "커밋이 해당 문서에 포함되어 있지 않음",
                                                value = """
                                                {
                                                    "status": 400,
                                                    "message": "커밋이 해당 문서에 속해있지 않습니다",
                                                    "error": "COMMIT_NOT_IN_DOCUMENT"
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
                                                    "message": "해당 기록를 찾을 수 없습니다.",
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
                                                name = "MySQL 저장 실패",
                                                value = """
                                                            {
                                                              "status": 500,
                                                              "message": "저장에 실패했습니다.",
                                                              "error": "FAIL_TO_SAVE_IN_MYSQL"
                                                            }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "MongoDB 저장 실패",
                                                value = """
                                                            {
                                                              "status": 500,
                                                              "message": "저장에 실패했습니다.",
                                                              "error": "FAILED_TO_SAVE_IN_MONGO"
                                                            }
                                                        """
                                        )
                                }
                        )
                )

        }
)
public @interface CreateBranchOrSaveDoc {

}
