package io.ejangs.docsa.domain.commit.api;

import io.ejangs.docsa.domain.commit.app.CommitService;
import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommitController {

    private final CommitService commitService;

    @PostMapping("/api/document/{docId}/commit")
    public ResponseEntity<CreateCommitResponse> createCommit(
            @PathVariable("docId") Long docId,
            @RequestBody @Valid CreateCommitRequest commitRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commitService.createCommit(docId, commitRequest));
    }

    @DeleteMapping("/api/document/{docId}/commit/{commitId}")
    public ResponseEntity<Void> deleteCommit(
            @PathVariable("docId") Long docId,
            @PathVariable("commitId") Long commitId) {

        commitService.deleteCommit(docId, commitId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
