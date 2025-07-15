package io.ejangs.docsa.domain.doc.api;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.DocTitleUpdateResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public ResponseEntity<DocCreateResponse> create(@RequestParam Long userId,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(docService.create(request, userId));
    }

    @GetMapping("/sidebar")
    public ResponseEntity<List<DocListSimpleResponse>> readListSidebar(
            @RequestParam Long userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getSimpleList(userId));
    }

    @PatchMapping("/{docId}")
    public ResponseEntity<DocTitleUpdateResponse> updateDocumentTitle(
            @PathVariable Long docId,
            @RequestParam Long userId,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.updateTitle(userId, docId, request));
    }

    @DeleteMapping("/{docId}")
    public void delete(@PathVariable Long docId, @RequestParam Long userId) {

    }

}
