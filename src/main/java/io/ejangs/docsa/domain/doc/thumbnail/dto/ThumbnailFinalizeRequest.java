package io.ejangs.docsa.domain.doc.thumbnail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ThumbnailFinalizeRequest(
        @NotNull
        Long imageId,

        @NotNull
        Long requestToken,

        @NotBlank
        String signature
) {

}
