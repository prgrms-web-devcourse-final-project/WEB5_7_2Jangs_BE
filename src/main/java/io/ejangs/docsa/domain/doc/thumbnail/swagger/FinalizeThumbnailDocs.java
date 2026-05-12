package io.ejangs.docsa.domain.doc.thumbnail.swagger;

import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailFinalizeRequest;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailResponse;
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
        summary = "문서 대표 썸네일 확정",
        description = """
                프론트가 생성한 썸네일 이미지를 문서의 대표 썸네일로 확정합니다.
                저장 API 응답의 `thumbnail.requestToken`과 프론트가 계산한 `signature`를 함께 전송해야 합니다.
                서버는 requestToken을 검증해 오래된 업로드 결과가 최신 썸네일을 덮어쓰지 못하게 막습니다.
                확정하려는 이미지는 같은 문서에 연결된 `DOC_THUMBNAIL` 목적의 `ACTIVE` 이미지여야 합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "docId",
                        description = "썸네일을 확정할 문서 id",
                        example = "1",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        requestBody = @RequestBody(
                required = true,
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ThumbnailFinalizeRequest.class),
                        examples = @ExampleObject(
                                value = """
                                        {
                                          "imageId": 10,
                                          "requestToken": 12,
                                          "signature": "9f0c1dd5d7e8b8f2a1f4c3d2e6a7b8c9"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "썸네일 확정 성공",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ThumbnailResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "imageId": 10,
                                                  "thumbnailUrl": "https://cdn.example.com/users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.webp",
                                                  "status": "READY",
                                                  "signature": "9f0c1dd5d7e8b8f2a1f4c3d2e6a7b8c9"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "썸네일 용도가 아닌 이미지",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "status": 400,
                                                  "message": "썸네일 용도의 이미지가 아닙니다.",
                                                  "error": "INVALID_THUMBNAIL_PURPOSE"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "401",
                        description = "인증 실패 - 로그인 세션이 없음",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
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
                        description = "문서, 이미지 또는 썸네일 정보를 찾을 수 없음",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "썸네일 정보 없음",
                                                value = """
                                                        {
                                                          "status": 404,
                                                          "message": "썸네일 정보를 찾을 수 없습니다.",
                                                          "error": "THUMBNAIL_NOT_FOUND"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "이미지 없음",
                                                value = """
                                                        {
                                                          "status": 404,
                                                          "message": "이미지를 찾을 수 없습니다.",
                                                          "error": "IMAGE_NOT_FOUND"
                                                        }
                                                        """
                                        )
                                }
                        )
                ),
                @ApiResponse(
                        responseCode = "409",
                        description = "오래된 requestToken 또는 S3 업로드 미완료",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "오래된 썸네일 요청",
                                                value = """
                                                        {
                                                          "status": 409,
                                                          "message": "최신 썸네일 요청이 아닙니다.",
                                                          "error": "STALE_THUMBNAIL_REQUEST"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "이미지 업로드 미완료",
                                                value = """
                                                        {
                                                          "status": 409,
                                                          "message": "이미지 업로드가 아직 완료되지 않았습니다.",
                                                          "error": "IMAGE_UPLOAD_NOT_COMPLETED"
                                                        }
                                                        """
                                        )
                                }
                        )
                )
        }
)
public @interface FinalizeThumbnailDocs {
}
