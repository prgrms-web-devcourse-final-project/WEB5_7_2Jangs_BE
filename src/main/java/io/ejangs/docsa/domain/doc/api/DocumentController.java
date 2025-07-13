package io.ejangs.docsa.domain.doc.api;

import io.ejangs.docsa.domain.doc.app.DocumentService;
import io.ejangs.docsa.domain.doc.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocumentTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocumentTitleUpdateResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentCreateResponse> create(@RequestParam Long userId,
            @Valid @RequestBody DocumentTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.create(request, userId));
    }

    @GetMapping("/sidebar")
    public ResponseEntity<List<DocumentListSimpleResponse>> readListSidebar(
            @RequestParam Long userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(documentService.getSimpleList(userId));
    }

    @PatchMapping("/{documentId}")
    public ResponseEntity<DocumentTitleUpdateResponse> updateDocumentTitle(
            @PathVariable Long documentId,
            @RequestParam Long userId,
            @Valid @RequestBody DocumentTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(documentService.updateTitle(userId, documentId, request));
    }

}
