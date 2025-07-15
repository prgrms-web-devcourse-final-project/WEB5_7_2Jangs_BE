package io.ejangs.docsa.domain.doc.dto;

import io.ejangs.docsa.domain.branch.dto.BranchDto;
import io.ejangs.docsa.domain.commit.dto.CommitDto;

import java.util.List;

public record CommitGraphResponse(
        String title,
        List<CommitDto> commits,
        List<EdgeDto> edges,
        List<BranchDto> branches
) {}
