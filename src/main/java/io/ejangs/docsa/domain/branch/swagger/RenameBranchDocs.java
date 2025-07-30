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
        summary = "버전 이름 변경",
        description = "버전의 이름을 수정합니다. 메인버전의 이름은 수정할 수 없습니다.",
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "수정하려는 버전가 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "branchId",
                        description = "수정하려는 버전의 id",
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
                                        	"newName" : "수정한 버전 이름"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "버전 이름 수정 성공",
                        content = @Content(
                                schema = @Schema(implementation = BranchRenameResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                	"id" : 1,
                                                	"name": "수정한 버전 이름"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "버전 이름 수정 실패 - 해당 문서에 속한 버전이 아님",
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
                        description = "버전 이름 수정 실패 - 해당 id를 가진 버전 없음",
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
                        description = "버전 이름 수정 실패 - MySQL 또는 MongoDB 저장 실패",
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
public @interface RenameBranchDocs {

}
