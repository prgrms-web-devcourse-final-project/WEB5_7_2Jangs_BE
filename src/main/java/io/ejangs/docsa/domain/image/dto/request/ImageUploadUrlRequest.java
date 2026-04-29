package io.ejangs.docsa.domain.image.dto.request;

import io.ejangs.docsa.domain.image.entity.Image.Purpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "이미지 업로드 URL 발급 요청")
public record ImageUploadUrlRequest(
        @Schema(description = "이미지가 연결될 문서 id", example = "1")
        @NotNull(message = "DocId bad request")
        Long docId,

        @Schema(description = "원본 파일명", example = "profile.png")
        @NotBlank(message = "OriginalFileName Cannot be BLANK")
        String originalFileName,

        @Schema(description = "이미지 Content-Type", example = "image/png")
        @NotBlank(message = "ContentType Cannot be BLANK")
        String contentType,

        @Schema(description = "파일 크기(byte)", example = "102400")
        @NotNull(message = "Size Cannot be Null")
        @Positive(message = "Size Must be Positive Number")
        Long size,

        @Schema(description = "이미지 업로드 목적(DOC_CONTENT/DOC_THUMBNAIL)", example = "DOC_CONTENT")
        @NotNull(message = "Purpose Cannot be Null")
        Purpose purpose
) {

}
