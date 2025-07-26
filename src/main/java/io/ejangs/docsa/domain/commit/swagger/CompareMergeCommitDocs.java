package io.ejangs.docsa.domain.commit.swagger;

import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
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
        summary = "머지할 2개의 커밋 내용을 조회합니다.",
        description = """
                유저가 소유한 문서에서 머지할 2개의 커밋 내용을 조회합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "커밋들이 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "baseId",
                        description = "base commit 의 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "targetId",
                        description = "target commit 의 id",
                        example = "2",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "2개의 기록 (commit) 조회 성공 - content 2개 반환",
                        content = @Content(
                                schema = @Schema(implementation = CompareMergeCommitResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "base": {
                                                      "id": "base123",
                                                      "title": "기록A 제목",
                                                      "content": "기록A 문서내용 ....."
                                                    },
                                                    "target": {
                                                      "id": "target456",
                                                      "title": "기록 B 제목",
                                                      "content": "기록B 문서내용 ....."
                                                    }
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
                        description = "기록 (commit) 실패",
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
        }
)
public @interface CompareMergeCommitDocs {

}
