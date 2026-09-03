package io.ejangs.docsa.domain.branch.api;

import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.request.BranchRenameRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.branch.swagger.CreateBranchDocs;
import io.ejangs.docsa.domain.branch.swagger.DeleteBranchDocs;
import io.ejangs.docsa.domain.branch.swagger.RenameBranchDocs;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/{documentId}/branch")
@Tag(name = "Branch API")
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @CreateBranchDocs
    public ResponseEntity<BranchCreateResponse> createBranch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Idempotency-Key") UUID operationId,
            @PathVariable Long documentId, @Valid @RequestBody BranchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchService.createBranch(
                        documentId, request, userDetails.getId(), operationId.toString()
                ));
    }

    @PatchMapping("/{branchId}")
    @RenameBranchDocs
    public ResponseEntity<BranchRenameResponse> renameBranch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long documentId, @PathVariable Long branchId,
            @Valid @RequestBody BranchRenameRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(branchService.renameBranch(documentId, branchId, request.newName(),
                        userDetails.getId()));
    }

    @DeleteMapping("/{branchId}")
    @DeleteBranchDocs
    public ResponseEntity<Void> deleteBranch(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long documentId, @PathVariable Long branchId){
        branchService.deleteBranch(documentId, branchId, userDetails.getId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
