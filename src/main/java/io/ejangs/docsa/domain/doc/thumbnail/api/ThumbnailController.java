package io.ejangs.docsa.domain.doc.thumbnail.api;

import io.ejangs.docsa.domain.doc.thumbnail.app.ThumbnailService;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailFinalizeRequest;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/{docId}/thumbnail")
public class ThumbnailController {

    private final ThumbnailService thumbnailService;

    @PutMapping
    public ResponseEntity<ThumbnailResponse> finalizeThumbnail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long docId,
            @Valid @RequestBody ThumbnailFinalizeRequest request
    ) {
        return ResponseEntity.ok(thumbnailService.finalizeThumbnail(
                userDetails.getId(),
                docId,
                request.imageId(),
                request.requestToken(),
                request.signature()
        ));
    }


}
