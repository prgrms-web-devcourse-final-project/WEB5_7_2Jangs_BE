package io.ejangs.docsa.domain.image.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImageUploadUrlRequest(
        @NotNull(message = "DocId bad request")
        Long docId,

        @NotBlank(message = "OriginalFileName Cannot be BLANK")
        String originalFileName,

        @NotBlank(message = "ContentType Cannot be BLANK")
        String contentType,

        @NotNull(message = "Size Cannot be Null")
        @Positive(message = "Size Must be Positive Number")
        Long size
) {

}
