package io.ejangs.docsa.domain.image.api;

import io.ejangs.docsa.domain.image.app.ImageService;
import io.ejangs.docsa.domain.image.dto.request.ImageUploadUrlRequest;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadCompleteResponse;
import io.ejangs.docsa.domain.image.dto.response.ImageUploadUrlResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/upload-url")
    public ResponseEntity<ImageUploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ImageUploadUrlRequest request) {

        return ResponseEntity.ok(
                imageService.createUploadUrl(userDetails.getId(), request)
        );
    }

    @PostMapping("/{imageId}/complete")
    public ResponseEntity<ImageUploadCompleteResponse> complete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long imageId
    ) {
        return ResponseEntity.ok(
                imageService.complete(userDetails.getId(), imageId)
        );
    }

}
