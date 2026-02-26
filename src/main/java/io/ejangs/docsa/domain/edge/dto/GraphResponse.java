package io.ejangs.docsa.domain.edge.dto;

import io.ejangs.docsa.domain.edge.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.edge.dto.graph.EdgeDto;

import java.util.List;

public record GraphResponse(
        String title,
        List<CommitGraphDto> commits,
        List<EdgeDto> edges,
        List<BranchGraphDto> branches
) {}
