package io.ejangs.docsa.domain.commit.app;

import io.ejangs.docsa.domain.commit.dto.request.CreateCommitRequest;
import io.ejangs.docsa.domain.commit.dto.response.CreateCommitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommitService {

    @Transactional
    public CreateCommitResponse createCommit(Long documentId, Long userId,
            CreateCommitRequest commitRequest) {
        return null;
    }
}
