package io.ejangs.docsa.domain.doc.api;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @PostMapping
    public ResponseEntity<DocCreateResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(docService.create(request, userDetails.getId()));
    }

    @GetMapping("/sidebar")
    public ResponseEntity<List<DocListSimpleResponse>> readListSidebar(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getSimpleList(userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<List<DocListResponse>> readList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getList(userDetails.getId()));
    }

    @PatchMapping("/{docId}")
    public ResponseEntity<DocTitleUpdateResponse> updateDocumentTitle(
            @PathVariable Long docId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.updateTitle(userDetails.getId(), docId, request));
    }

    @GetMapping("/{docId}/graph")
    public ResponseEntity<CommitGraphResponse> getGraph(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long docId) {
        return ResponseEntity.status(HttpStatus.OK).body(docService.getGraph(userDetails.getId(), docId));
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@PathVariable Long docId, @RequestParam Long userId) {
        docService.delete(userId, docId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT).build();
    }
}
