package io.ejangs.docsa.domain.doc.thumbnail.api;

import io.ejangs.docsa.domain.doc.thumbnail.app.ThumbnailService;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailFinalizeRequest;
import io.ejangs.docsa.domain.doc.thumbnail.dto.ThumbnailResponse;
import io.ejangs.docsa.domain.doc.thumbnail.swagger.FinalizeThumbnailDocs;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Thumbnail API", description = "문서 대표 썸네일 API")
public class ThumbnailController {

    private final ThumbnailService thumbnailService;

    @PutMapping
    @FinalizeThumbnailDocs
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
