package io.ejangs.docsa.domain.save.api;

import io.ejangs.docsa.domain.save.dto.response.SaveUpdateResponse;
import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "유저가 요청한 저장 id에 해당하는 저장 수정",
        description = """
                유저가 소유한 문서의 저장을 수정합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "documentId",
                        description = "저장이 속한 문서 id",
                        example = "1L",
                        required = true,
                        in = ParameterIn.PATH
                ),
                @Parameter(
                        name = "saveId",
                        description = "조회하려는 저장 id",
                        example = "1L",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "저장 수정 성공",
                        content = @Content(
                                schema = @Schema(implementation = SaveUpdateResponse.class)
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "저장 수정 실패 - 요청을 보낸 유저의 저장이 아님",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class)
                        )
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 로그인 세션이 없음",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                ),
                @ApiResponse(
                        responseCode = "404",
                        description = "저장 수정 실패 - 존재하지 않는 저장",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class)
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "저장 수정 실패 - MySQL에 저장을 실패함.",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class)
                        )
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "저장 수정 실패 - MongoDB에 저장을 실패함.",
                        content = @Content(
                                schema = @Schema(implementation = ErrorResponse.class)
                        )
                ),
        }
)
public @interface UpdateSaveDocs {

}
