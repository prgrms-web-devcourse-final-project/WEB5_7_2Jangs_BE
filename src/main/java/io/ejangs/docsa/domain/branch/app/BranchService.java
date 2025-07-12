package io.ejangs.docsa.domain.branch.app;

import io.ejangs.docsa.domain.branch.dto.BranchCreateRequest;
import io.ejangs.docsa.domain.branch.dto.BranchCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    @Transactional
    public BranchCreateResponse createBranch(Long documentId,
            BranchCreateRequest request) {
        return null;
    }
}


