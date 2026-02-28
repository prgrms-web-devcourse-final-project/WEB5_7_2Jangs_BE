package io.ejangs.docsa.domain.doc.api;

import io.ejangs.docsa.domain.doc.app.DocService;
import io.ejangs.docsa.domain.doc.dto.request.DocTitleRequest;
import io.ejangs.docsa.domain.edge.dto.GraphResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocCreateResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocPageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.ejangs.docsa.domain.doc.dto.response.DocTitleUpdateResponse;
import io.ejangs.docsa.domain.doc.swagger.CreateDocDocs;
import io.ejangs.docsa.domain.doc.swagger.DeleteDocDocs;
import io.ejangs.docsa.domain.doc.swagger.GetDocGraphDocs;
import io.ejangs.docsa.domain.doc.swagger.GetDocListDocs;
import io.ejangs.docsa.domain.doc.swagger.GetDocSidebarListDocs;
import io.ejangs.docsa.domain.doc.swagger.RenameDocDocs;
import io.ejangs.docsa.domain.doc.swagger.SearchDocDocs;
import io.ejangs.docsa.domain.save.util.PageableFactory;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequiredArgsConstructor
@Tag(name = "Document API")
@RequestMapping("/api/document")
public class DocController {

    private final DocService docService;

    @PostMapping
    @CreateDocDocs
    public ResponseEntity<DocCreateResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(docService.create(request, userDetails.getId()));
    }

    @GetMapping("/sidebar")
    @GetDocSidebarListDocs
    public ResponseEntity<Page<DocSimplePageResponse>> readListSidebar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageableFactory.create(sort, order, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getSimplePage(userDetails.getId(), pageable));
    }

    @GetMapping
    @GetDocListDocs
    public ResponseEntity<Page<DocPageResponse>> readList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageableFactory.create(sort, order, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.getPage(userDetails.getId(), pageable));
    }

    @SearchDocDocs
    @GetMapping("/search")
    public ResponseEntity<Page<DocPageResponse>> search(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageableFactory.create(sort, order, page, size);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.searchList(userDetails.getId(), keyword, pageable));
    }

    @RenameDocDocs
    @PatchMapping("/{docId}")
    public ResponseEntity<DocTitleUpdateResponse> rename(
            @PathVariable Long docId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DocTitleRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(docService.updateTitle(userDetails.getId(), docId, request));
    }

    @GetDocGraphDocs
    @GetMapping("/{docId}/graph")
    public ResponseEntity<GraphResponse> getGraph(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long docId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(docService.getGraph(userDetails.getId(), docId));
    }

    @DeleteDocDocs
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long docId) {
        docService.delete(docId, userDetails.getId());
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT).build();
    }
}
