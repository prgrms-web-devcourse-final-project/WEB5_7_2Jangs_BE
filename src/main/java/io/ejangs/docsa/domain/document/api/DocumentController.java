package io.ejangs.docsa.domain.document.api;

import io.ejangs.docsa.domain.document.app.DocumentService;
import io.ejangs.docsa.domain.document.dto.DocumentCreateRequest;
import io.ejangs.docsa.domain.document.dto.DocumentCreateResponse;
import io.ejangs.docsa.domain.document.dto.DocumentListSidebarResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            @Valid @RequestBody DocumentCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(documentService.create(request, userId));
    }

    @GetMapping("/sidebar")
    public ResponseEntity<List<DocumentListSidebarResponse>> readListSidebar(
            @RequestParam Long userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(documentService.readSidebar(userId));
    }

}
