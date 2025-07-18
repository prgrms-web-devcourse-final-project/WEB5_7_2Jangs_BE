package io.ejangs.docsa.domain.doc.api;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocListSimpleResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
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

    @GetMapping
    public ResponseEntity<List<DocListResponse>> readList(@RequestParam Long userId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getList(userId));
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

    @GetMapping("/{docId}/graph")
    public ResponseEntity<CommitGraphResponse> getGraph(@RequestParam Long userId,
            @PathVariable Long docId) {
        return ResponseEntity.status(HttpStatus.OK).body(docService.getGraph(userId, docId));
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@PathVariable Long docId, @RequestParam Long userId) {
        docService.delete(userId, docId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT).build();
    }
}
