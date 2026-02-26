package io.ejangs.docsa.domain.doc.dto.response;

import io.ejangs.docsa.domain.doc.dto.graph.BranchGraphDto;
import io.ejangs.docsa.domain.doc.dto.graph.CommitGraphDto;
import io.ejangs.docsa.domain.doc.dto.graph.EdgeDto;

import java.util.List;

public record GraphResponse(
        String title,
        List<CommitGraphDto> commits,
        List<EdgeDto> edges,
        List<BranchGraphDto> branches
) {}
