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
        summary = "문서 제목 검색",
        description = """
                로그인한 사용자의 문서 중 제목에 해당 키워드가 포함된 문서를 검색합니다.
                검색 결과는 페이지네이션 및 정렬 조건(`sort`, `order`)에 따라 반환됩니다.
                기본 정렬 필드는 `updatedAt`, 정렬 순서는 `desc`입니다.
                """,
        parameters = {
                @Parameter(
                        name = "keyword",
                        description = "검색할 키워드",
                        example = "기획서",
                        required = true,
                        in = ParameterIn.QUERY
                ),
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
                        description = "문서 검색 성공",
                        content = @Content(
                                schema = @Schema(implementation = PageDocListResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "content": [
                                                        {
                                                            "id": 3,
                                                            "title": "9월 기획서",
                                                            "createdAt": "2025-07-24T23:08:36.088612+09:00",
                                                            "updatedAt": "2025-07-24T23:08:36.114375+09:00",
                                                            "preview": "미리보기 없음",
                                                            "recentSaveId": 3
                                                        },
                                                        {
                                                            "id": 2,
                                                            "title": "6월 기획서",
                                                            "createdAt": "2025-07-24T23:08:32.066607+09:00",
                                                            "updatedAt": "2025-07-24T23:08:32.088371+09:00",
                                                            "preview": "미리보기 없음",
                                                            "recentSaveId": 2
                                                        },
                                                        {
                                                            "id": 1,
                                                            "title": "5월 기획서",
                                                            "createdAt": "2025-07-24T23:08:27.511586+09:00",
                                                            "updatedAt": "2025-07-24T23:08:27.648299+09:00",
                                                            "preview": "미리보기 없음",
                                                            "recentSaveId": 1
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
                                                    "totalElements": 3,
                                                    "totalPages": 1,
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
                        description = "문서 검색 실패 - 존재하지 않는 커밋",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 기록을 찾을 수 없습니다.",
                                                    "error": "COMMIT_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "문서 검색 실패 - MySQL 또는 MongoDB 접근 실패",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = {
                                        @ExampleObject(
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
public @interface SearchDocDocs {

}
