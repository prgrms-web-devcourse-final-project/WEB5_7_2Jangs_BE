package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.commit.dto.CommitDto;
import io.ejangs.docsa.domain.doc.dto.EdgeDto;

import java.util.List;

public record CommitGraphResponse(
        String title,
        List<CommitDto> commits,
        List<EdgeDto> edges,
        List<BranchDto> branches
) {}
