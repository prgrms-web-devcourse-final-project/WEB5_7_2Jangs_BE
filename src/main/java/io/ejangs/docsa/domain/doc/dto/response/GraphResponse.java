package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.doc.dto.graph.GraphCommitDto;
import io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto;

import java.util.List;

public record GraphResponse(
        String title,
        List<GraphCommitDto> commits,
        List<GraphEdgeDto> edges,
        List<BranchGraphDto> branches
) {}
