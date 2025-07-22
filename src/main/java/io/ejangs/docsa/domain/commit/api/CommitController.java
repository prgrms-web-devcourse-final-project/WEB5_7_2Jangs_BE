package io.ejangs.docsa.domain.commit.api;

import io.ejangs.docsa.domain.commit.app.CommitService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.request.MergeCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CompareMergeCommitResponse;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
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
/*
@AuthenticationPrincipal CustomUserDetails userDetails 을 추가해놓음.
추후 다른 PR에서 서비스 로직에서 관련 유효성 검사를 진행할 예정
 */
@RestController
@RequiredArgsConstructor
public class CommitController {

    private final CommitService commitService;

    @PostMapping("/api/document/{docId}/commit")
    public ResponseEntity<CreateCommitResponse> createCommit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("docId") Long docId,
            @RequestBody @Valid CreateCommitRequest commitRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commitService.createCommit(docId, commitRequest));
    }

    @GetMapping("/api/document/{docId}/commit/{commitId}")
    public ResponseEntity<CommitResponse> getCommit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("docId") Long docId,
            @PathVariable("commitId") Long commitId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(commitService.getCommit(docId, commitId));
    }

    @GetMapping("/api/document/{docId}/merge")
    public ResponseEntity<CompareMergeCommitResponse> compareMergeCommit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("docId") Long docId,
            @RequestParam("base") Long baseId,
            @RequestParam("target") Long targetId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(commitService.compareCommitForMerge(docId, baseId, targetId));
    }

    @PostMapping("/api/document/{docId}/merge")
    public ResponseEntity<CreateCommitResponse> mergeCommit(
            @PathVariable("docId") Long docId,
            @RequestBody @Valid MergeCommitRequest mergeRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commitService.mergeCommit(docId, mergeRequest));
    }
}
