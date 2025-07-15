package io.ejangs.docsa.domain.branch.api;

import io.ejangs.docsa.domain.branch.app.BranchService;
import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import io.ejangs.docsa.domain.branch.dto.BranchRenameRequest;
import io.ejangs.docsa.domain.branch.dto.BranchRenameResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/{documentId}/branch")
public class BranchController {

    private final BranchService branchService;

    // 브랜치에 이어서 새로운 저장 생성 or 새로운 브랜치 + 저장 생성
    @PostMapping
    public ResponseEntity<BranchCreateResponse> createBranchOrSave(@RequestParam Long userId,
            @PathVariable Long documentId, @Valid @RequestBody BranchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchService.createBranchOrSave(documentId, request, userId));
    }

    // 브랜치 이름 수정
    @PatchMapping("/{branchId}")
    public ResponseEntity<BranchRenameResponse> renameBranch(@RequestParam Long userId,
            @PathVariable Long documentId, @PathVariable Long branchId,
            @Valid @RequestBody BranchRenameRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(branchService.renameBranch(documentId, branchId, request.newName(), userId));
    }
}
