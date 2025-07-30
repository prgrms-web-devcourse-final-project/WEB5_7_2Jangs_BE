package io.ejangs.docsa.domain.doc.swagger;

import io.ejangs.docsa.domain.doc.dto.swagger.PageDocListResponse;
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
        summary = "전체 문서 목록 조회",
        description = """
                로그인한 사용자의 문서 목록을 페이지네이션과 정렬 옵션에 따라 조회합니다.
                정렬 필드는 기본적으로 updatedAt이며, order는 desc가 기본값입니다.
                """,
        parameters = {
                @Parameter(
                        name = "sort",
                        description = "정렬 기준 필드 (예: updatedAt, createdAt)",
                        example = "updatedAt",
                        in = ParameterIn.QUERY
                ),
                @Parameter(
                        name = "order",
                        description = "정렬 순서 (asc 또는 desc)",
                        example = "desc",
                        in = ParameterIn.QUERY
                ),
                @Parameter(
                        name = "page",
                        description = "페이지 번호 (0부터 시작)",
                        example = "0",
                        in = ParameterIn.QUERY
                ),
                @Parameter(
                        name = "size",
                        description = "페이지 크기",
                        example = "10",
                        in = ParameterIn.QUERY
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "문서 목록 조회 성공",
                        content = @Content(
                                schema = @Schema(implementation = PageDocListResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "content": [
                                                        {
                                                            "id": 3,
                                                            "title": "새 문서 제목 3",
                                                            "createdAt": "2025-07-22T04:48:43.036421+09:00",
                                                            "updatedAt": "2025-07-22T04:48:43.065674+09:00",
                                                            "preview": "이문서의 마지막 저장or커밋의 일부분미리보기 부분~~",
                                                            "recent": {
                                                                "recentType": "SAVE",
                                                                "recentTypeId": 3
                                                            }
                                                        },
                                                        {
                                                            "id": 2,
                                                            "title": "새 문서 제목 2",
                                                            "createdAt": "2025-07-22T04:48:40.02776+09:00",
                                                            "updatedAt": "2025-07-22T04:48:40.056721+09:00",
                                                            "preview": "미리보기 없음",
                                                            "recent": {
                                                                "recentType": "SAVE",
                                                                "recentTypeId": 2
                                                            }
                                                        },
                                                        {
                                                            "id": 1,
                                                            "title": "새 문서 제목",
                                                            "createdAt": "2025-07-22T04:48:35.242338+09:00",
                                                            "updatedAt": "2025-07-22T04:48:35.348708+09:00",
                                                            "preview": "미리보기 없음",
                                                            "recent": {
                                                                "recentType": "SAVE",
                                                                "recentTypeId": 1
                                                            }
                                                        }
                                                    ],
                                                    "pageable": {
                                                        "pageNumber": 0,
                                                        "pageSize": 10,
                                                        "sort": {
                                                            "empty": false,
                                                            "sorted": true,
                                                            "unsorted": false
                                                        },
                                                        "offset": 0,
                                                        "paged": true,
                                                        "unpaged": false
                                                    },
                                                    "last": true,
                                                    "totalPages": 1,
                                                    "totalElements": 3,
                                                    "first": true,
                                                    "size": 10,
                                                    "number": 0,
                                                    "sort": {
                                                        "empty": false,
                                                        "sorted": true,
                                                        "unsorted": false
                                                    },
                                                    "numberOfElements": 3,
                                                    "empty": false
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "정렬 실패 - 정렬 옵션이 잘못됨",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
                                                name = "정렬 기준이 잘못됨",
                                                value = """
                                                        {
                                                             "status": 400,
                                                             "message": "지원하지 않는 정렬 기준입니다.",
                                                             "error": "UNSUPPORTED_SORT_TYPE"
                                                         }
                                                        """),
                                        @ExampleObject(
                                                name = "정렬 방향이 잘못됨",
                                                value = """
                                                        {
                                                              "status": 400,
                                                              "message": "지원하지 않는 정렬 방향입니다.",
                                                              "error": "UNSUPPORTED_DIRECTION_TYPE"
                                                        }
                                                        """),
                                        @ExampleObject(
                                                name = "페이지 크기가 잘못됨",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "페이지 크기는 0 이상이어야 합니다.",
                                                            "error": "INVALID_PAGE_SIZE"
                                                        }
                                                        """),
                                        @ExampleObject(
                                                name = "페이지 번호가 잘못됨",
                                                value = """
                                                        {
                                                            "status": 400,
                                                            "message": "페이지 번호는 0 이상이어야 합니다.",
                                                            "error": "INVALID_PAGE"
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
                        description = "존재하지 않는 데이터",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                @ExampleObject(
                                        value = """
                                                    {
                                                      "status": 404,
                                                      "message": "해당 저장 데이터를 찾을 수 없습니다.",
                                                      "error": "SAVE_NOT_FOUND"
                                                    }
                                                """,
                                        name = "저장을 찾을 수 없음."
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "서버오류로 인한 문서 조회 실패.",
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
public @interface GetDocListDocs {

}
