package io.ejangs.docsa.domain.branch.swagger;

import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "브랜치 삭제",
        description = """
                브랜치를 삭제합니다. 삭제 조건은 아래와 같습니다:

                - 메인 브랜치(`fromCommit == null`)는 삭제 불가
                - 다른 브랜치가 이 브랜치를 기반(fromCommit)으로 만들어졌다면 삭제 불가
                - 블록, 시퀀스, 저장(MongoDB)도 함께 삭제됨
                """,
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "문서 ID",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "branchId",
                        description = "삭제할 브랜치 ID",
                        example = "5",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "브랜치 삭제 성공"
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "브랜치 삭제 실패 - 삭제 불가능한 브랜치",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "메인 브랜치 삭제 시도",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "메인 브랜치는 삭제할 수 없습니다.",
                                                          "error": "MAIN_BRANCH_FIX_UNAVAILABLE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "파생 브랜치가 존재",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "해당 브랜치로부터 파생된 브랜치가 존재합니다.",
                                                          "error": "SUB_BRANCH_DELETE_UNAVAILABLE"
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
                        description = "브랜치 삭제 실패 - 문서 또는 브랜치가 존재하지 않음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "status": 404,
                                                  "message": "해당 브랜치 또는 문서를 찾을 수 없습니다.",
                                                  "error": "BRANCH_NOT_FOUND_OR_FORBIDDEN"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "브랜치 삭제 실패 - MySQL 또는 MongoDB 저장 실패",
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
public @interface DeleteBranchDocs {
}
