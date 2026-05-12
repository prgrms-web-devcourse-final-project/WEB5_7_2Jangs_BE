package io.ejangs.docsa.domain.image.swagger;

import io.ejangs.docsa.domain.image.dto.request.ImageUploadUrlRequest;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadUrlResponse;
import io.ejangs.docsa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
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
        summary = "이미지 업로드용 Presigned URL 발급",
        description = """
                문서에 업로드할 이미지의 presigned PUT URL을 발급합니다.
                응답으로 받은 `uploadUrl`에 클라이언트가 직접 PUT 업로드를 수행한 뒤
                `POST /api/images/{imageId}/complete` API를 호출해야 업로드가 완료됩니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        requestBody = @RequestBody(
                required = true,
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ImageUploadUrlRequest.class),
                        examples = @ExampleObject(
                                value = """
                                        {
                                          "docId": 1,
                                          "originalFileName": "profile.png",
                                          "contentType": "image/png",
                                          "size": 102400,
                                          "purpose": "DOC_CONTENT"
                                        }
                                        """
                        )
                )
        ),
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Presigned URL 발급 성공",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ImageUploadUrlResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "imageId": 10,
                                                  "objectKey": "users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.png",
                                                  "uploadUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/...",
                                                  "method": "PUT",
                                                  "expiresInSeconds": 300
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "400",
                        description = "잘못된 이미지 타입 또는 크기",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "지원하지 않는 이미지 형식",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "지원하지 않는 이미지 형식입니다.",
                                                          "error": "INVALID_IMAGE_CONTENT_TYPE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "허용 용량 초과",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "업로드 가능한 최대 용량을 초과했습니다.",
                                                          "error": "INVALID_IMAGE_SIZE"
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
                        description = "존재하지 않는 문서",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "status": 404,
                                                  "message": "해당 문서를 찾을 수 없습니다.",
                                                  "error": "DOCUMENT_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                )
        }
)
public @interface CreateImageUploadUrlDocs {
}
