package io.ejangs.docsa.domain.branch.api;

import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.request.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.request.BranchRenameRequest;
import io.ejangs.docsa.domain.branch.dto.response.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.response.BranchRenameResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/{documentId}/branch")
public class BranchController {

    private final BranchService branchService;

    // 브랜치에 이어서 새로운 저장 생성 or 새로운 브랜치 + 저장 생성
    @PostMapping
    public ResponseEntity<BranchCreateResponse> createBranchOrSave(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long documentId, @Valid @RequestBody BranchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchService.createBranchOrSave(documentId, request, userDetails.getId()));
    }

    // 브랜치 이름 수정
    @PatchMapping("/{branchId}")
    public ResponseEntity<BranchRenameResponse> renameBranch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long documentId, @PathVariable Long branchId,
            @Valid @RequestBody BranchRenameRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(branchService.renameBranch(documentId, branchId, request.newName(),
                        userDetails.getId()));
    }

    // 브랜치 삭제
    @DeleteMapping("/{branchId}")
    public ResponseEntity<Void> deleteBranch(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long documentId, @PathVariable Long branchId){
        branchService.deleteBranch(documentId, branchId, userDetails.getId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
