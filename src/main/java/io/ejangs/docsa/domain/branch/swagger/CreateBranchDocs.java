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
        summary = "새 브랜치를 생성합니다.",
        description = """
                선택한 기록(fromCommitId)을 기준으로 새로운 브랜치와 작업장(save)을 생성합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "브랜치를 생성할 문서 ID",
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
                        schema = @Schema(implementation = BranchCreateRequest.class),
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "name": "feature-branch",
                                            "fromCommitId": 10
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "브랜치와 작업장 생성 성공",
                        content = @Content(
                                schema = @Schema(implementation = BranchCreateResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "branchId": 3,
                                                    "saveId": 4
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "브랜치 생성 실패 - 잘못된 요청",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "fromCommitId가 null인 경우",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "이어서 작업할 커밋을 선택해주세요.",
                                                            "error": "VALIDATION_FAILED"
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
                                                name = "생성하려는 브랜치 이름이 문서 내에서 중복됨",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "새로운 브랜치의 이름은 다른 브랜치의 이름과 중복될 수 없습니다.",
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
                        description = "브랜치 생성 실패 - 존재하지 않는 데이터",
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
                        responseCode = "409",
                        description = "생성 작업 충돌 - CREATE_OPERATION_IN_PROGRESS, "
                                + "CREATE_OPERATION_CANCELLED 또는 IDEMPOTENCY_KEY_REUSED",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "브랜치 생성 실패 - MySQL 또는 MongoDB 저장 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        name = "MySQL 또는 MongoDB에 저장 실패",
                                        value = """
                                                {
                                                    "status": 500,
                                                    "message": "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                                                    "error": "DATABASE_ERROR"
                                                }
                                                """
                                )
                        )
                )

        }
)
public @interface CreateBranchDocs {

}
