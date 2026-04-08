package io.ejangs.docsa.domain.branch.merge.api;

import io.ejangs.docsa.domain.branch.merge.app.MergeService;
import io.ejangs.docsa.domain.branch.merge.dto.request.MergeRequest;
import io.ejangs.docsa.domain.branch.merge.dto.response.CompareMergeResponse;
import io.ejangs.docsa.domain.branch.merge.dto.response.MergeResponse;
import io.ejangs.docsa.domain.branch.merge.swagger.CompareMergeDocs;
import io.ejangs.docsa.domain.branch.merge.swagger.MergeDocs;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tags({
        @Tag(name = "Merge API")
})
public class MergeController {

    private final MergeService mergeService;


    @GetMapping("/api/document/{docId}/merge")
    @CompareMergeDocs
    public ResponseEntity<CompareMergeResponse> compare(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("docId") Long docId,
            @RequestParam("base") Long baseId,
            @RequestParam("target") Long targetId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(mergeService.compare(docId, baseId, targetId, userDetails.getId()));
    }

    @PostMapping("/api/document/{docId}/merge")
    @MergeDocs
    public ResponseEntity<MergeResponse> merge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("docId") Long docId,
            @RequestBody @Valid MergeRequest mergeRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mergeService.merge(docId, mergeRequest, userDetails.getId()));
    }
}
