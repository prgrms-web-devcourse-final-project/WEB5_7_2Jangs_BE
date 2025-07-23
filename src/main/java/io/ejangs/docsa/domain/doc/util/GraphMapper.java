package io.ejangs.docsa.domain.doc.util;

import io.ejangs.docsa.domain.doc.dto.graph.GraphBranchDto;
import io.ejangs.docsa.domain.branch.entity.Branch;
import io.ejangs.docsa.domain.doc.dto.graph.GraphCommitDto;
import io.ejangs.docsa.domain.commit.entity.Commit;
import io.ejangs.docsa.domain.doc.dto.graph.GraphEdgeDto;
import io.ejangs.docsa.domain.doc.dto.response.CommitGraphResponse;
import io.ejangs.docsa.domain.doc.entity.Edge;

import java.util.List;

public class GraphMapper {

    public static GraphCommitDto toCommitDto(Commit commit) {
        return new GraphCommitDto(
                commit.getId(),
                commit.getBranch().getId(),
                commit.getTitle(),
                commit.getDescription(),
                commit.getCreatedAt()
        );
    }

    public static GraphEdgeDto toEdgeDto(Edge edge) {
        return new GraphEdgeDto(
                edge.getPrevCommit().getId(),
                edge.getNextCommit().getId()
        );
    }

    public static GraphBranchDto toBranchDto(Branch branch) {
        return new GraphBranchDto(
                branch.getId(),
                branch.getName(),
                branch.getCreatedAt(),
                branch.getFromCommit() != null ? branch.getFromCommit().getId() : null,
                branch.getRootCommit() != null ? branch.getRootCommit().getId() : null,
                branch.getLeafCommit() != null ? branch.getLeafCommit().getId() : null,
                branch.getSave() != null ? branch.getSave().getId() : null
        );
    }

    public static CommitGraphResponse toCommitGraphResponse(String title,
            List<GraphCommitDto> commits,
            List<GraphEdgeDto> edges,
            List<GraphBranchDto> branches
    ) {
        return new CommitGraphResponse(title, commits, edges, branches);
    }
}
