package io.ejangs.docsa.domain.commit.swagger;

import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "기록 삭제",
        description = """
                기록을 삭제합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                
                삭제 조건은 아래와 같습니다:
                
                - 각 브랜치의 LeafCommit만 삭제 가능
                - 어느 브랜치의 FromCommit이면 삭제 불가
                - 브랜치의 RootCommit은 삭제 불가(RootCommit까지 삭제하고 싶은 경우 브랜치 삭제를 권장합니다)
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "기록 삭제하려는 문서의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "commitId",
                        description = "삭제하려는 기록의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "기록 삭제 성공"
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "기록 삭제 실패 - 삭제 불가능한 기록",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "삭제하려는 기록이 LeafCommit이 아닐 경우 삭제할 수 없다.",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "브랜치의 마지막 기록이 아닙니다.",
                                                            "error": "IS_NOT_LEAF_COMMIT"
                                                        }
                                                        """

                                        ),
                                        @ExampleObject(
                                                name = "삭제하려는 기록이 FromCommit, RootCommit인 경우 삭제할 수 없다.",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "이 기록은 삭제할 수 없습니다.",
                                                            "error": "CAN_NOT_DELETE_COMMIT"
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
                        description = "기록 삭제 실패 - 존재하지 않는 데이터",
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
                        description = "기록 삭제 실패 - MySQL 또는 MongoDB 데이터 처리 실패",
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
                                        ),
                                }
                        )
                )
        }
)
public @interface DeleteCommitDocs {

}
