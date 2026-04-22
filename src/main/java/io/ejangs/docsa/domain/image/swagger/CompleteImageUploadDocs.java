package io.ejangs.docsa.domain.image.swagger;

import io.ejangs.docsa.domain.image.dto.response.ImageUploadCompleteResponse;
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
        summary = "이미지 업로드 완료 처리",
        description = """
                presigned URL로 S3 업로드가 끝난 뒤 호출합니다.
                서버는 S3 `HeadObject`로 실제 업로드된 객체를 검증한 뒤 이미지를 `ACTIVE` 상태로 전환합니다.
                아직 업로드되지 않았거나 업로드가 실패한 경우 `IMAGE_UPLOAD_NOT_COMPLETED`를 반환합니다.
                🔐 이 API는 세션 로그인 상태에서 호출되어야 하며,
                클라이언트는 쿠키(`JSESSIONID`)를 통해 인증 정보를 전송해야 합니다.
                """,
        parameters = {
                @Parameter(
                        name = "imageId",
                        description = "업로드 URL 발급 시 반환된 이미지 id",
                        example = "10",
                        required = true,
                        in = ParameterIn.PATH
                )
        },
        responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "업로드 완료 처리 성공",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ImageUploadCompleteResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "imageId": 10,
                                                  "objectKey": "users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.png",
                                                  "imageUrl": "https://cdn.example.com/users/1/docs/1/images/550e8400-e29b-41d4-a716-446655440000.png",
                                                  "contentType": "image/png",
                                                  "size": 102400,
                                                  "status": "ACTIVE"
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
                        description = "존재하지 않는 이미지 메타데이터",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = @ExampleObject(
                                        value = """
                                                {
                                                  "status": 404,
                                                  "message": "이미지를 찾을 수 없습니다.",
                                                  "error": "IMAGE_NOT_FOUND"
                                                }
                                                """
                                )
                        )
                ),
                @ApiResponse(
                        responseCode = "409",
                        description = "S3 업로드 미완료 또는 업로드된 파일 검증 실패",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                        @ExampleObject(
                                                name = "업로드 미완료",
                                                value = """
                                                        {
                                                          "status": 409,
                                                          "message": "이미지 업로드가 아직 완료되지 않았습니다.",
                                                          "error": "IMAGE_UPLOAD_NOT_COMPLETED"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "콘텐츠 타입 불일치",
                                                value = """
                                                        {
                                                          "status": 400,
                                                          "message": "지원하지 않는 이미지 형식입니다.",
                                                          "error": "INVALID_IMAGE_CONTENT_TYPE"
                                                        }
                                                        """
                                        ),
                                        @ExampleObject(
                                                name = "파일 크기 불일치",
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
                )
        }
)
public @interface CompleteImageUploadDocs {
}
