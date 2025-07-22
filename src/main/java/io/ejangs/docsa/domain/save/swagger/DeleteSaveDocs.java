package io.ejangs.docsa.domain.save.swagger;

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
        summary = "유저가 요청한 저장 id에 해당하는 저장 삭제",
        description = """
                유저가 소유한 문서의 저장을 삭제합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "저장이 속한 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "saveId",
                        description = "조회하려는 저장 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "204",
                        description = "저장 삭제 성공"
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "저장 수정 실패 - 요청을 보낸 유저의 저장이 아님",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 400,
                                                    "message": "잘못된 접근입니다",
                                                    "error": "SAVE_NOT_OWNER"
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
                        description = "저장 수정 실패 - 존재하지 않는 저장",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                    "status": 404,
                                                    "message": "해당 저장 데이터를 찾을 수 없습니다.",
                                                    "error": "SAVE_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "저장 삭제 실패 - MySQL 또는 MongoDB 실패",
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
                                                              "message": "삭제를 실패했습니다.",
                                                              "error": "FAILED_TO_DELETE_IN_MONGO"
                                                            }
                                                        """
                                        )
                                }
                        )
                )
        }
)
public @interface DeleteSaveDocs {

}
