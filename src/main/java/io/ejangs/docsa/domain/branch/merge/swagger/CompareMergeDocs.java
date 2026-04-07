package io.ejangs.docsa.domain.branch.merge.swagger;

import io.ejangs.docsa.domain.branch.merge.dto.response.CompareMergeResponse;
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
        summary = "병합할 2개의 기록을 조회합니다.",
        description = """
                유저가 소유한 문서에서 병합할 2개의 기록을 조회합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "기록들이 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "base",
                        description = "base commit 의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.QUERY
                ),
                @Parameter(
                        name = "target",
                        description = "target commit 의 id",
                        example = "2",
                        required = true,
                        in = ParameterIn.QUERY
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "2개의 기록(commit) 조회 성공 - 병합 비교용 블록 목록 반환",
                        content = @Content(
                                schema = @Schema(implementation = CompareMergeResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "base": [
                                                        {
                                                            "id": "mhTl6ghSkV",
                                                            "type": "paragraph",
                                                            "data": {
                                                                "text": "기준 기록의 첫 문단입니다."
                                                            }
                                                        }
                                                    ],
                                                    "target": [
                                                        {
                                                            "id": "l98dyx3yjb",
                                                            "type": "header",
                                                            "data": {
                                                                "text": "비교 기록 제목",
                                                                "level": 3
                                                            }
                                                        },
                                                        {
                                                            "id": "os_YI4eub4",
                                                            "type": "paragraph",
                                                            "data": {
                                                                "text": "비교 기록에서 수정된 문단입니다."
                                                            }
                                                        }
                                                    ]
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
                        description = "기록(commit) 실패 - 존재하지 않는 데이터",
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
                        description = "기록 조회 실패 - MySQL 또는 MongoDB 데이터 처리 실패",
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
public @interface CompareMergeDocs {

}
