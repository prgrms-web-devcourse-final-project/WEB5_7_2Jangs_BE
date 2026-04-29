package io.ejangs.docsa.domain.doc.swagger;

import io.ejangs.docsa.domain.edge.dto.GraphResponse;
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
        summary = "문서 하나의 기록 그래프 조회",
        description = "문서 ID를 기반으로 커밋 간선 정보와 브랜치 정보를 포함한 그래프 데이터를 조회합니다.",
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "그래프를 조회할 문서 ID",
                        in = ParameterIn.PATH,
                        required = true,
                        example = "1"
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "그래프 조회 성공",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = GraphResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                	"title": "문서 제목",
                                                    "commits": [
                                                		    {
                                                				    "id": 11,
                                                				    "branchId": 101,
                                                				    "title": "이건 첫번째 커밋",
                                                				    "description": "first",
                                                				    "createdAt": "2025-07-07T12:00:00"
                                                		    },
                                                		    {
                                                				    "id": 12,
                                                				    "branchId": 101,
                                                				    "title": "이건 두번째 커밋",
                                                				    "description": "second",
                                                				    "createdAt": "2025-07-07T15:00:00"
                                                		    },
                                                		    {
                                                				    "id": 13,
                                                				    "branchId": 101,
                                                				    "title": "이건 세번째 커밋",
                                                				    "description": "third",
                                                				    "createdAt": "2025-07-07T18:00:00"
                                                		    },
                                                		    {
                                                				    "id": 14,
                                                				    "branchId": 102,
                                                				    "title": "이건 네번째 커밋",
                                                				    "description": "12에서 파생된 커밋",
                                                				    "createdAt": "2025-07-07T20:00:00"
                                                		    }
                                                    ],
                                                    "edges": [
                                                		    {
                                                				    "from": 11,
                                                				    "to": 12
                                                		    },
                                                		    {
                                                				    "from": 12,
                                                				    "to": 13
                                                		    },
                                                		    {
                                                				    "from": 12,
                                                				    "to": 14
                                                		    }
                                                    ],
                                                    "branches": [
                                                		    {
                                                				    "id": 101,
                                                				    "name": "main",
                                                				    "createdAt": "2025-07-07T12:00:00",
                                                				    "fromCommitId": null,
                                                				    "mergeTargetCommitId": null,
                                                				    "rootCommitId": 11,
                                                				    "leafCommitId": 13,
                                                				    "saveId": null
                                                		   },
                                                		    {
                                                				    "id": 102,
                                                				    "name": "sub1",
                                                				    "createdAt": "2025-07-07T20:00:00",
                                                				    "fromCommitId": 12,
                                                				    "mergeTargetCommitId": null,
                                                				    "rootCommitId": 14,
                                                				    "leafCommitId": 14,
                                                				    "saveId": 1001
                                                		    },
                                                		    {
                                                				    "id": 103,
                                                				    "name": "merge-branch",
                                                				    "createdAt": "2025-07-07T21:00:00",
                                                				    "fromCommitId": 12,
                                                				    "mergeTargetCommitId": 14,
                                                				    "rootCommitId": null,
                                                				    "leafCommitId": null,
                                                				    "saveId": 1002
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
                        description = "존재하지 않는 데이터",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                value = """
                                                            {
                                                              "status": 404,
                                                              "message": "해당 문서를 찾을 수 없습니다.",
                                                              "error": "DOCUMENT_NOT_FOUND"
                                                            }
                                                        """,
                                                name = "문서를 찾을 수 없음."
                                        ),
                                        @ExampleObject(
                                                value = """
                                                            {
                                                              "status": 404,
                                                              "message": "해당 브랜치를 찾을 수 없습니다.",
                                                              "error": "BRANCH_NOT_FOUND"
                                                            }
                                                        """,
                                                name = "브랜치를 찾을 수 없음."
                                        ),

                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "서버오류로 인한 그래프 조회 실패.",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 500,
                                                    "message": "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                                                    "error": "DATABASE_ERROR"
                                                }
                                                """
                                )
                        )
                ),

        }
)
public @interface GetDocGraphDocs {

}
