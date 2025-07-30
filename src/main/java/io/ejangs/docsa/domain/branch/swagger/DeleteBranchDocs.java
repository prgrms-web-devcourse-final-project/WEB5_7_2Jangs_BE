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
        summary = "버전 삭제",
        description = """
                버전를 삭제합니다. 삭제 조건은 아래와 같습니다:

                - 메인 버전(`fromCommit == null`)는 삭제 불가
                - 다른 버전이 이 버전를 기반(fromCommit)으로 만들어졌다면 삭제 불가
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
                        description = "삭제할 버전 ID",
                        example = "5",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "버전 삭제 성공"
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "버전 삭제 실패 - 삭제 불가능한 버전",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "메인 버전 삭제 시도",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "기본 버전은 삭제 또는 수정할 수 없습니다.",
                                                          "error": "MAIN_BRANCH_FIX_UNAVAILABLE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "파생된 버전이 존재",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "서브 버전이 있어 해당 버전을 삭제할 수 없습니다.",
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
                        description = "버전 삭제 실패 - 문서 또는 버전이 존재하지 않음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 버전을 찾을 수 없습니다.",
                                                    "error": "BRANCH_NOT_FOUND_OR_FORBIDDEN"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "버전 삭제 실패 - MySQL 또는 MongoDB 저장 실패",
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
public @interface DeleteBranchDocs {
}
