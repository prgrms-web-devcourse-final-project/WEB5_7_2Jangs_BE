package io.ejangs.docsa.domain.branch.swagger;

import io.ejangs.docsa.domain.branch.dto.request.BranchRenameRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
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
        summary = "브랜치 이름 변경",
        description = "브랜치의 이름을 수정합니다. 메인브랜치의 이름은 수정할 수 없습니다.",
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "수정하려는 브랜치가 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "branchId",
                        description = "수정하려는 브랜치의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        requestBody = @RequestBody(
                content = @Content(
                        schema = @Schema(implementation = BranchRenameRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                        	"newName" : "수정한 브랜치 이름"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "브랜치 이름 수정 성공",
                        content = @Content(
                                schema = @Schema(implementation = BranchRenameResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                	"id" : 1,
                                                	"name": "수정한 브랜치 이름"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "브랜치 이름 수정 실패 - 해당 문서에 속한 브랜치가 아님",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples =
                                @ExampleObject(
                                        value = """
                                                {
                                                   "status": 400,
                                                   "message": "해당 버전을 찾을 수 없습니다.",
                                                   "error": "BRANCH_NOT_FOUND_OR_FORBIDDEN"
                                                }
                                                """
                                )

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
                        description = "브랜치 이름 수정 실패 - 해당 id를 가진 브랜치 없음",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples =
                                @ExampleObject(
                                        value = """
                                                {
                                                   "status": 404,
                                                   "message": "해당 버전을 찾을 수 없습니다.",
                                                   "error": "BRANCH_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "브랜치 이름 수정 실패 - MySQL 또는 MongoDB 저장 실패",
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
public @interface RenameBranchDocs {

}
